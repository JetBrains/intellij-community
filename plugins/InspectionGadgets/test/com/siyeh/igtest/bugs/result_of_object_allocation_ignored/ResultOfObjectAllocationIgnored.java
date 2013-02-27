package com.siyeh.igtest.bugs.result_of_object_allocation_ignored;

public class ResultOfObjectAllocationIgnored {

    private ResultOfObjectAllocationIgnored() {
        super();
    }

    public static void foo() {
        new Integer(3);
    }

    void boom() {
        new Comparable<String>() {

            public int compareTo(String o) {
                return 0;
            }
        };
    }
}