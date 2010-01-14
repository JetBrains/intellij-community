package com.siyeh.igtest.finalization.finalize_calls_super_finalize;

public class FinalizeDoesntCallSuperFinalizeInspection
{
    public FinalizeDoesntCallSuperFinalizeInspection()
    {
    }

    protected void finalize()
    {

    }

    class X {
        @Override
        protected void finalize() throws Throwable {
            System.out.println("");
        }
    }
}