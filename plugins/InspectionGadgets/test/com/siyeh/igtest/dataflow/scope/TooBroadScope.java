package com.siyeh.igtest.dataflow.scope;

import java.util.*;

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

    void doNotNarrowInsideLambda() {
        final int[] counter = new int[1];
        Runnable runnable = () -> {
            counter[0] += 1;
            System.out.println("counter = " + counter);
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


    final int NON_STATIC_CONSTANT = value();

    private int value() {
        return 1;
    }

    void useConstant() {
        int <warning descr="Scope of variable 'yes' is too broad">yes</warning> = NON_STATIC_CONSTANT;
        System.out.println();
        System.out.println();
        System.out.println();
        System.out.println(yes);
    }

    private final Map<String, String> important = new HashMap<>();
    public void processImportant() {
        final HashMap copy = new HashMap(important);
        important.clear();
        System.out.println();
        System.out.println(copy);
    }

    private final String[] EMPTY = {};
    private final String[] ONE = {"one"};
    void abc() {
        String[] <warning descr="Scope of variable 'strings' is too broad">strings</warning> = EMPTY;
        System.out.println();
        System.out.println();
        System.out.println(strings);

        List<String> <warning descr="Scope of variable 'list' is too broad">list</warning> = new ArrayList<>(Arrays.asList(EMPTY));
        System.out.println();
        System.out.println();
        System.out.println(list);

        String[] <warning descr="Scope of variable 'ss' is too broad">ss</warning> = new String[10];
        System.out.println();
        System.out.println();
        System.out.println(ss);

        String[] <warning descr="Scope of variable 'ss2' is too broad">ss2</warning> = new String[] {""};
        System.out.println();
        System.out.println();
        System.out.println(ss2);

        String[] <warning descr="Scope of variable 'ss3' is too broad">ss3</warning> = {};
        System.out.println();
        System.out.println();
        System.out.println(ss3);

        String[] ss3a = {ONE[0]};
        backgroundAction();
        System.out.println();
        System.out.println(ss3a);

        List<String> ss4 = Arrays.asList(ONE);
        backgroundAction();
        System.out.println();
        System.out.println(ss4);
    }

    void backgroundAction() {
        ONE[0] = "two";
    }

    void time() {
        long start = System.currentTimeMillis();
        System.out.println();
        long end = System.currentTimeMillis();
        System.out.println("elapsed: " + (end - start));
    }

    void m(HashMap<Integer, String> src) {
        ArrayList<String> strings = new ArrayList<>(src.values());
        src.clear();
        for (String s : strings) {
            System.out.println(s);
        }
    }
}
class T {

    private Object[] array = {};

    public void foo(boolean value) {
        final int size = array.length;

        reinitArray();

        System.out.println(size);
    }

    private void reinitArray() {
        array = new String[5];
    }
}

class TryWithResources {

    void m() throws Throwable {
        AutoCloseable <warning descr="Scope of variable 'closeable1' is too broad">closeable1</warning> = null;
        try (closeable1) {}

        AutoCloseable <warning descr="Scope of variable 'closeable2' is too broad">closeable2</warning> = null;
        try (closeable2) {
            System.out.println(closeable2);
        }

        AutoCloseable closeable3 = null;
        try (closeable3) {
            System.out.println(closeable3);
        }
        System.out.println(closeable3);
    }
}