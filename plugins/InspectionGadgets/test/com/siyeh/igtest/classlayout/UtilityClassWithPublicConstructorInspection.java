package com.siyeh.igtest.classlayout;

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
}
