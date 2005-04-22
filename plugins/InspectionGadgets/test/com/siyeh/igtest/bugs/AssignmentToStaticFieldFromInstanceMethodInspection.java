package com.siyeh.igtest.bugs;

public class AssignmentToStaticFieldFromInstanceMethodInspection {
    private static int x = 32;

    public void foo()
    {
        x = 3;
        x++;
        --x;
    }

    public static void foo2()
    {
        x = 3;
        x++;
        --x;
    }
}
