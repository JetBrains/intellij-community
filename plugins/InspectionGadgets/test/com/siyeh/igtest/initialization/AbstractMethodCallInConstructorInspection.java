package com.siyeh.igtest.initialization;

public abstract class AbstractMethodCallInConstructorInspection
{

    protected AbstractMethodCallInConstructorInspection()
    {
        fooBar();
    }

    public abstract void fooBar();
}
