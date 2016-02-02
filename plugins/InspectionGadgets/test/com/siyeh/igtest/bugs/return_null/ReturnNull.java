package com.siyeh.igtest.bugs;

public class ReturnNull
{

    public Object bar()
    {
        return <warning descr="Return of 'null'">null</warning>;
    }

    public int[] bar2()
    {
        return <warning descr="Return of 'null'">null</warning>;
    }
}