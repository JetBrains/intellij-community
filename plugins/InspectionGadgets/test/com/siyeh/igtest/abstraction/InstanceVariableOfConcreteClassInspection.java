package com.siyeh.igtest.abstraction;

import com.siyeh.igtest.classlayout.AbstractClass;
import com.siyeh.igtest.classlayout.ConstantInterface;

public class InstanceVariableOfConcreteClassInspection
{
    private Object foo = barangus();
    private AbstractClass bar = (AbstractClass)barangus();
    private ConstantInterface baz = (ConstantInterface)barangus();

    public static void main(String[] args)
    {

    }

    private static Object barangus()
    {
        return null;
    }
}
