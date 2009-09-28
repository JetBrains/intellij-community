package com.siyeh.igtest.finalization;

public class FinalizeDoesntCallSuperFinalizeInspection
{
    public FinalizeDoesntCallSuperFinalizeInspection()
    {
    }

    protected void finalize()
    {

    }
}