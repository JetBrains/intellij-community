package com.siyeh.igtest.abstraction;

import com.siyeh.igtest.classlayout.AbstractClass;
import com.siyeh.igtest.classlayout.ConstantInterface;

public class StaticVariableOfConcreteClassInspection
{
    private static Object foo = barangus();
    private static AbstractClass bar = (AbstractClass)barangus();
    private static ConstantInterface baz = (ConstantInterface)barangus();

    public static void main(String[] args)
    {

    }

    private static Object barangus()
    {
        return null;
    }
}
