package com.siyeh.igtest.finalization;

public class FinalizeNotProtectedInspection
{
    public FinalizeNotProtectedInspection()
    {
    }

    public void finalize() throws Throwable
    {
        super.finalize();
    }
}