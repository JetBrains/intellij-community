package com.siyeh.igtest.abstraction;

import com.siyeh.igtest.classlayout.AbstractClass;
import com.siyeh.igtest.classlayout.ConstantInterface;


public class CastToConcreteClassInspection
{
    public static void main(String[] args)
    {
        Object foo = null;
        final AbstractClass abstractFoo = (AbstractClass)foo;
        final ConstantInterface interfaceFoo = (ConstantInterface)foo;
    }
}
