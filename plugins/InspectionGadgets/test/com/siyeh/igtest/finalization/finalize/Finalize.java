package com.siyeh.igtest.finalization.finalize;

public class Finalize
{
    public Finalize() throws Throwable
    {
        super();
    }

    protected void <warning descr="'finalize()' should not be overridden">finalize</warning>() throws Throwable
    {
        super.finalize();
    }
    
    class X {
        @Override
        protected void finalize() throws Throwable {
        }
    }

}