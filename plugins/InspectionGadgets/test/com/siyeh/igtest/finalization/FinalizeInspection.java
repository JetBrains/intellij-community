package com.siyeh.igtest.finalization;

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

}