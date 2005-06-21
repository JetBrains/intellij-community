package com.siyeh.igtest.confusing;

public class ReuseOfLocalVariableInspection {
    public ReuseOfLocalVariableInspection() {
    }

    public void fooBar(int bar, int baz) {
        int i = 3;
        System.out.println(i);
        i = 4;
        System.out.println(i);
        if(true)
        {
            i = 5;
            System.out.println(i);
        }
        for(int j = 0;j<3;j++)
        {
            System.out.println(j);
        }
    }
}

