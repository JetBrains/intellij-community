package com.siyeh.igtest.classlayout.utility_class_with_public_constructor;

public class UtilityClassWithPublicConstructorInspection
{
    public static final int CONSTANT = 1;

    public UtilityClassWithPublicConstructorInspection()
    {

    }

    public static int barangus()
    {
        return CONSTANT;
    }

    static class MyClass
    {
        public MyClass()
        {
        }
    }

    static class X {
        static int t = 9;
        int i = 0;

        public X () {}
    }
}
