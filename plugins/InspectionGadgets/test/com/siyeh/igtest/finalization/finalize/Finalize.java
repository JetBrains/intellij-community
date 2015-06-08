package com.siyeh.igtest.finalization.finalize;

public class Finalize
{
    public Finalize() throws Throwable
    {
        super();
    }

    protected void <warning descr="'finalize()' declared">finalize</warning>() throws Throwable
    {
        super.finalize();
    }
    
    class X {
        @Override
        protected void finalize() throws Throwable {
        }
    }

}