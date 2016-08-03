package com.siyeh.igtest.finalization.finalize_calls_super_finalize;

import java.lang.Override;
import java.lang.Throwable;

public class FinalizeCallsSuperFinalize
{
    public FinalizeCallsSuperFinalize()
    {
    }

    @Override
    protected void finalize()
    {
        System.out.println("something");
    }

    class X extends FinalizeCallsSuperFinalize {
        @Override
        protected void <warning descr="Method 'finalize()' does not call 'super.finalize()'">finalize</warning>() throws <error descr="'finalize()' in 'com.siyeh.igtest.finalization.finalize_calls_super_finalize.FinalizeCallsSuperFinalize.X' clashes with 'finalize()' in 'com.siyeh.igtest.finalization.finalize_calls_super_finalize.FinalizeCallsSuperFinalize'; overridden method does not throw 'java.lang.Throwable'">Throwable</error> {
            System.out.println("");
        }
    }

    class Y extends FinalizeCallsSuperFinalize {
        protected void <warning descr="Method 'finalize()' does not call 'super.finalize()'">finalize</warning>() throws <error descr="'finalize()' in 'com.siyeh.igtest.finalization.finalize_calls_super_finalize.FinalizeCallsSuperFinalize.Y' clashes with 'finalize()' in 'com.siyeh.igtest.finalization.finalize_calls_super_finalize.FinalizeCallsSuperFinalize'; overridden method does not throw 'java.lang.Throwable'">Throwable</error> {
            if (false) {
                super.finalize(); // not reached, thus not called.
            }
            System.out.println("non trivial");
        }
    }
}
class A {
    public void finalize(int random) {}
    protected void finalize() {
        System.out.println("non trivial");
    }
}
class B extends A {
    protected void <warning descr="Method 'finalize()' does not call 'super.finalize()'">finalize</warning>() throws <error descr="'finalize()' in 'com.siyeh.igtest.finalization.finalize_calls_super_finalize.B' clashes with 'finalize()' in 'com.siyeh.igtest.finalization.finalize_calls_super_finalize.A'; overridden method does not throw 'java.lang.Throwable'">Throwable</error> {
        super.finalize(1);
    }
}