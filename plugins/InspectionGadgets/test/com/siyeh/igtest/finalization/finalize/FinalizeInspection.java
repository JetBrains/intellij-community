package com.siyeh.igtest.finalization.finalize;

public class FinalizeInspection
{
    public FinalizeInspection() throws Throwable
    {
        super();
    }

    protected void finalize() throws Throwable
    {
        super.finalize();
    }
    
    class X {
        @Override
        protected void finalize() throws Throwable {
        }
    }

}