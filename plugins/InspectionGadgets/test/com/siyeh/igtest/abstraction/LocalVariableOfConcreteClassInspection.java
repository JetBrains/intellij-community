package com.siyeh.igtest.abstraction;

import com.siyeh.igtest.classlayout.AbstractClass;
import com.siyeh.igtest.classlayout.ConstantInterface;


public class LocalVariableOfConcreteClassInspection
{
    public static void main(String[] args)
    {
        Object foo = barangus();
        AbstractClass bar = (AbstractClass)barangus();
        ConstantInterface baz = (ConstantInterface)barangus();
    }

    private static Object barangus()
    {   
    }
}
