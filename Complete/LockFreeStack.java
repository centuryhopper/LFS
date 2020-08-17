import java.sql.Struct;
import java.time.Duration;
import java.time.Instant;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class LockFreeStack<T>
{
    private static void CheckThreadCompletion(Thread [] threads)
    {
        boolean allThreadsAreDone = false;
        while (!allThreadsAreDone)
        {
            for (Thread t : threads)
            {
                if (t.isAlive())
                {
                    allThreadsAreDone = false;
                    break;
                }
                allThreadsAreDone = true;
            }
            // break will take us here
        }
    }

    private static void createRNDList(Random rnd, ConcurrentLinkedQueue<Integer> list, int upper)
    {
        for (int i = 0; i < upper; i++)
        {
            list.add(rnd.nextInt(101) + 1);
        }
    }

    private static void createNodeList(Random rnd, ConcurrentLinkedQueue<Node<Integer>> nodes, int upper)
    {
        for (int i = 0; i < upper; i++)
        {
            nodes.add(new Node<Integer>(rnd.nextInt(101)));
        }
    }

    public static void main(String[] args)
    {
        StackWorker<Integer> sw = new StackWorker<>();
        Random rnd = new Random();
        int numThreads = 4, numNodes = 600000, numRands = 150000;
        Thread[] threads = new Thread[numThreads];
        ConcurrentLinkedQueue<Integer> list = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<Node<Integer>> nodes = new ConcurrentLinkedQueue<>();

        @SuppressWarnings("rawtypes")
        MyThreads [] myThreads = new MyThreads[numThreads];

        // pre-populate stack with 50,000 elements
        for (int i = 0; i < 50000; i++)
        {
            sw.push(new Node<Integer>(rnd.nextInt(101)));
        }

        System.out.println();
        System.out.println("Before running the threads --> " + sw);

        // make 600,000 Node<Integer> references
        createNodeList(rnd, nodes, numNodes);

        // create and run threads
        for (int i = 0; i < numThreads; i++)
        {
            // create list of random numbers for probability testing
            createRNDList(rnd, list, numRands);

            myThreads[i] = new MyThreads<Integer>(sw, i + 1, list, nodes);
            threads[i] = new Thread(myThreads[i]);
            System.out.printf("thread %d: ", i + 1);
            Instant start = Instant.now();
            threads[i].start();
            try {
                threads[i].join();
            } catch (Exception e) {
                System.out.println("exception caught");
            }
            Instant end = Instant.now();
            System.out.println(Duration.between(start, end).toMillis() + " ms");
        }

        CheckThreadCompletion(threads);

        System.out.println("After running the threads --> " + sw);
        System.out.println();
    }
}

class MyThreads<T> implements Runnable
{
    private StackWorker<T> sw;
    private int id;
    private ConcurrentLinkedQueue<Integer> randNumList;
    private ConcurrentLinkedQueue<Node<T>> nodes;

    public MyThreads(StackWorker<T> sw,  int id,
    ConcurrentLinkedQueue<Integer> list,
    ConcurrentLinkedQueue<Node<T>> nodes)
    {
        this.sw = sw;
        this.id = id;
        this.randNumList = list;
        this.nodes = nodes;
    }

    public int getId()
    {
        return this.id;
    }

    @Override
    public void run()
    {
        // 50% size, 25% push, 25% pop in this example
        for (int i = 0; i < 150000; i++)
        {
            int num = randNumList.poll();
            if (num <= 50 && num > 0)
            {
                sw.getSize();
                sw.OpsCAS();
            }
            else if (num > 50 && num <= 75)
            {
                sw.pop();
            }
            else
            {
                sw.push(nodes.poll());
            }
        }
    }
}

class Node<T>
{
    T val;
    Node<T> next;
    
    Node(T _val)
    {
        val = _val;
    }
}

class StackWorker<T>
{
    private AtomicReference<Node<T>> head;
    private AtomicInteger numOps = new AtomicInteger(0);
    private AtomicInteger size = new AtomicInteger(0);

    public StackWorker()
    {
        head = new AtomicReference<Node<T>>();
    }

    public int getOps()
    {
        return numOps.get();
    }

    public boolean OpsCAS()
    {
        int tmp = getOps();
        int newOps = tmp + 1;
        return numOps.compareAndSet(tmp, newOps);
    }

    public int getSize()
    {
        return size.get();
    }

    public Node<T> getHead()
    {
        return head.get();
    }

    public boolean push(Node<T> node)
    {
        // compare and swap for head
        node.next = getHead();
        head.compareAndSet(node.next, node);

        // compare and swap for numOps
        OpsCAS();

        // compare and swap for size
        int tmp2 = getSize();
        int newSize = tmp2 + 1;
        size.compareAndSet(tmp2, newSize);
        return true;
    }

    public T pop()
    {
        if (getHead() == null)
        {
            return null;
        }

        // compare and swap for head
        Node<T> n = getHead();
        head.compareAndSet(n, n.next);
        
        // compare and swap for numOps
        OpsCAS();

        // compare and swap for size
        int tmp2 = getSize();
        int newSize = tmp2 - 1;
        size.compareAndSet(tmp2, newSize);
        return n.val;
    }

    @Override
    public String toString()
    {
        return "Total number of operations: " + getOps() +
        ", Current size: " + getSize();
    }
}
