package com.siyeh.igtest.abstraction;

import com.siyeh.igtest.classlayout.AbstractClass;
import com.siyeh.igtest.classlayout.ConstantInterface;

public class MethodReturnOfConcreteClassInspection
{

    private Object foo(Object bar)
    {
       return null;
    }

    private static AbstractClass barangus(AbstractClass baz)
    {
        return null;
    }

    private static ConstantInterface barangus(ConstantInterface baz)
    {
        return null;
    }
}
