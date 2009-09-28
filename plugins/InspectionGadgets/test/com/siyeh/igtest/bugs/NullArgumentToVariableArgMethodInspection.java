package com.siyeh.igtest.bugs;

public class NullArgumentToVariableArgMethodInspection {
    public void foo()
    {
        String.format("%s", null);
        String.format("%s", new int[]{1, 2, 3});
    }
}
