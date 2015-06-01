package com.siyeh.igtest.finalization.finalize_calls_super_finalize;

public class FinalizeCallsSuperFinalize
{
    public FinalizeCallsSuperFinalize()
    {
    }

    protected void finalize()
    {

    }

    class X {
        @Override
        protected void <warning descr="'finalize()' does not call 'super.finalize()'">finalize</warning>() throws Throwable {
            System.out.println("");
        }
    }
}