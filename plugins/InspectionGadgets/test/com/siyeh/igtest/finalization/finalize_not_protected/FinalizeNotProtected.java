package com.siyeh.igtest.finalization;

public class FinalizeNotProtected
{
    public FinalizeNotProtected()
    {
    }

    public void <warning descr="'finalize()' not declared 'protected'">finalize</warning>() throws Throwable
    {
        super.finalize();
    }
}
class FinalizeProtected {

    protected void finalize() throws Throwable {
        super.finalize();
    }
}
interface Finalizer {

    void finalize();
}