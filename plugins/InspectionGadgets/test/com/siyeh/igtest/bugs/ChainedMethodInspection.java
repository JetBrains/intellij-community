package com.siyeh.igtest.bugs;

public class ChainedMethodInspection{
    private ChainedMethodInspection  baz =  foo().bar();
    public void baz(){
        foo().bar();
        (foo()).bar();
    }

    public ChainedMethodInspection foo()
    {
        return this;
    }

    public ChainedMethodInspection bar()
    {
        return this;
    }
}
