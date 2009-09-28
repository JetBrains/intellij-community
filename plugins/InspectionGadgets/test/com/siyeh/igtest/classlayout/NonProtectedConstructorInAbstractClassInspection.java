package com.siyeh.igtest.classlayout;

public abstract class NonProtectedConstructorInAbstractClassInspection
{
    public NonProtectedConstructorInAbstractClassInspection()
    {
    }
    private NonProtectedConstructorInAbstractClassInspection(int foo)
    {
    }
}
