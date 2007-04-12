package com.siyeh.igtest.bugs;

public class ResultOfObjectAllocationIgnoredInspection {

    private ResultOfObjectAllocationIgnoredInspection() {
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