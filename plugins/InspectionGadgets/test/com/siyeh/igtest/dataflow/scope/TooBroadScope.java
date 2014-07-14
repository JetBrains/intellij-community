package com.siyeh.igtest.dataflow.scope;

import java.util.ArrayList;
import java.util.Collection; import java.util.List;

public class TooBroadScope
{
    // Option "Only report variables that can be moved to inner blocks" is OFF
    public void test() {
        // Example #1
        {
            Collection<Integer> <warning descr="Scope of variable 'list' is too broad">list</warning>  = null; //scope too broad
            {
                list = new ArrayList<Integer>();
                list.add(new Integer(0));
            }
        }

        // Example #2
        {

            Collection<Integer> <warning descr="Scope of variable 'list' is too broad">list</warning>; // scope too broad
            list = new ArrayList<Integer>();
            list.add(new Integer(0));
        }

        // Example #3
        {

            Collection<Integer> <warning descr="Scope of variable 'list' is too broad">list</warning>  = null; // nope
            list = new ArrayList<Integer>();
            list.add(new Integer(0));
        }
    }

    public void join() {
        String <warning descr="Scope of variable 'test' is too broad">test</warning>;
        test = "asdf";
    }

    private int foo() {
        final int flim;
        final boolean bar = new java.util.Random().nextBoolean();
        if(bar) {
            flim = 42;
        } else {
            flim = 24;
        }
        return flim;
    }

    void foo(String a, String b, List c) {
        for (int d = 0, cannotNarrowMyScope; d < a.length(); d = cannotNarrowMyScope + b.length()) {
            cannotNarrowMyScope = a.indexOf(b, d);

            if (cannotNarrowMyScope < 0) {
                c.add(a.substring(d));
                break;
            } else {
                c.add(a.substring(d, cannotNarrowMyScope));
            }
        }
    }

    public int hashCode() {
        int result = 17;
        result = 37 * result;
        return result;
    }

    public void operatorAssignment() {
        int i = 10;
        i -= 1;
    }

    private String variableUsedAsArgument(String s) {
        String separator = "";
        separator = variableUsedAsArgument(separator);
        return null;
    }

    void doNotNarrowInsideAnonymousClass() {
        final int[] counter = new int[1];
        Runnable runnable = new Runnable()
        {
            public void run()
            {
                counter[0] += 1;
                System.out.println("counter = " + counter);
            }
        };
        runnable.run();
    }

    void switchLabel() {
        final int other = 4;
        switch (2)
        {
            case other:
                System.out.println("other");
            default:
                System.out.println("default");
      }
    }

    void forLoop() {
        int <warning descr="Scope of variable 'i' is too broad">i</warning>  = 0;
        for ( ; i < 10; i++) {
            System.out.println(i);
        }
    }

    void resourceVariable(boolean b) throws Exception {
        try (AutoCloseable ac = null) {
            if (b) {
                System.out.println(ac);
            }
        }
    }
}