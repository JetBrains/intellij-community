package com.siyeh.igtest.abstraction;

import com.siyeh.igtest.classlayout.AbstractClass;
import com.siyeh.igtest.classlayout.ConstantInterface;


public class InstanceofInterfacesInspection
{
    public static void main(String[] args)
    {
        final Object foo = barangus();
        if(foo instanceof AbstractClass)
        {
            return;
        }
        if(foo instanceof ConstantInterface)
        {
            return;
        }
    }

    private static Object barangus()
    {
    }
}
