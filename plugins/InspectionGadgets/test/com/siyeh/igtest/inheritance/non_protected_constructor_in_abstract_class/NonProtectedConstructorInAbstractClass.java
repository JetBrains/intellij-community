package com.siyeh.igtest.classlayout;

public abstract class NonProtectedConstructorInAbstractClass
{
    public <warning descr="Constructor 'NonProtectedConstructorInAbstractClass()' is not declared 'protected' in 'abstract' class">NonProtectedConstructorInAbstractClass</warning>()
    {
    }
    private NonProtectedConstructorInAbstractClass(int foo)
    {
    }

    public <error descr="Illegal type: 'void'">void</error>();
}
