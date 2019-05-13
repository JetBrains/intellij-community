package com.siyeh.igtest.bugs.result_of_object_allocation_ignored;

public class ResultOfObjectAllocationIgnored {

    private ResultOfObjectAllocationIgnored() {
        super();
    }

    public static void foo() {
        new <warning descr="Result of 'new Integer()' is ignored">Integer</warning>(3);
        new java.util.ArrayList();
    }

    void boom() {
        new <warning descr="Result of 'new Comparable<String>()' is ignored">Comparable<String></warning>() {

            public int compareTo(String o) {
                return 0;
            }
        };
    }

    Throwable switchExpression(int i) {
        return switch(i) {
            default -> new Throwable();
        };
    }
}