package com.siyeh.igtest.abstraction;

import java.util.ArrayList;
import java.util.List;
import java.util.AbstractList;


public class OverlyStrongTypeCastInspection
{
    public static void main(String[] args)
    {
        List bar = new ArrayList();
        AbstractList foo = (ArrayList) bar;
        List foo2 = (List) bar;
        double x = (double)3.0f;
    }
}
