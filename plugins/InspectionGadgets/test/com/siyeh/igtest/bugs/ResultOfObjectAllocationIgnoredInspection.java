package com.siyeh.igtest.bugs;

public class ResultOfObjectAllocationIgnoredInspection {
    private ResultOfObjectAllocationIgnoredInspection() {
        super();
    }

    public static void foo()
    {
        new Integer(3);
    }
}
