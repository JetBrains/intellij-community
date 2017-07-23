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
interface A<T> {
    T m();
}
class B implements A<Void> {
    public Void m() {
        return  null;
    }
}