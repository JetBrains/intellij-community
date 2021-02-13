package com.siyeh.igtest.dataflow.scope;

import java.util.*;

public class TooBroadScope
{
    private String string = "";

    public String getString() {
        return string;
    }

    void process() {
        final String string = getString();
        this.string = "hello";
        System.out.println();
        System.out.println(string);
    }

    public void systemProperty() {
        String prop = System.getProperty("iesd.licensing.clt");

        System.setProperty("iesd.licensing.clt", "true");

        if (prop == null) {
            System.clearProperty("iesd.licensing.clt");
        } else {
            System.setProperty("iesd.licensing.clt", prop);
        }
    }

    private String getBaseFontInfo() {
        final StringBuilder sb = new StringBuilder();
        final var sm = System.getSecurityManager();
        sb.append(sm.getSecurityContext());
        sb.append(',');
        sb.append(sm.getClass());
        return sb.toString();
    }

    String allowMovingStringBuilder() {
        System.out.println();
        final StringBuilder sb = new StringBuilder();
        sb.append(1);
        return sb.toString();
    }

    void playThatRecord() {
        record Record(double rpm) {}
        System.out.println();
        final var record = new Record(33.3333333333);
        System.out.println(record);
    }

    void noClassCastException() {
        a b;
        b renderer = new b();
    }

    void looseThreads() {
        Map before = Thread.getAllStackTraces();
        System.out.println();
        System.out.println();
        System.out.println();
        System.out.println(before);
    }

  String brokenCode() {
    var n. = "awesome[]";
    return n;
  }

    // Option "Only report variables that can be moved to inner blocks" is OFF
    public void test() {
        // Example #1
        {
            {
                //scope too broad
                Collection<Integer> list = new ArrayList<Integer>();
                list.add(new Integer(0));
            }
        }

        // Example #2
        {

            // scope too broad
            Collection<Integer> list = new ArrayList<Integer>();
            list.add(new Integer(0));
        }

        // Example #3
        {

            // nope
            Collection<Integer> list = new ArrayList<Integer>();
            list.add(new Integer(0));
        }
    }

    public void join() {
        String test = "asdf";
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
        for (int i = 0; i < 10; i++) {
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
        System.out.println();
        System.out.println();
        System.out.println();
        int yes = NON_STATIC_CONSTANT;
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
        System.out.println();
        System.out.println();
        String[] strings = EMPTY;
        System.out.println(strings);

        System.out.println();
        System.out.println();
        List<String> list = new ArrayList<>(Arrays.asList(EMPTY));
        System.out.println(list);

        System.out.println();
        System.out.println();
        String[] ss = new String[10];
        System.out.println(ss);

        System.out.println();
        System.out.println();
        String[] ss2 = new String[]{""};
        System.out.println(ss2);

        System.out.println();
        System.out.println();
        String[] ss3 = {};
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
        try (AutoCloseable closeable1 = null) {}

        try (AutoCloseable closeable2 = null) {
            System.out.println(closeable2);
        }

        AutoCloseable closeable3 = null;
        try (closeable3) {
            System.out.println(closeable3);
        }
        System.out.println(closeable3);

        String s = "file.name";
        try (java.io.FileInputStream in = new java.io.FileInputStream(s)) {}
    }
}