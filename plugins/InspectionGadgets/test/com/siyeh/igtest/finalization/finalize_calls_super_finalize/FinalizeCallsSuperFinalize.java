package com.siyeh.igtest.finalization.finalize_calls_super_finalize;

import java.lang.Override;
import java.lang.Throwable;

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

    class Y {
        protected void <warning descr="'finalize()' does not call 'super.finalize()'">finalize</warning>() throws Throwable {
            if (false) {
                super.finalize(); // not reached, thus not called.
            }
            System.out.println("non trivial");
        }
    }
}