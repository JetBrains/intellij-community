package com.siyeh.igtest.abstraction;

import java.util.ArrayList;
import java.util.List;
import java.util.AbstractList;
import java.lang.reflect.Array;

interface TestInter{}

public class OverlyStrongTypeCastInspection
{
    public static void main(String[] args)
    {
        List bar = new ArrayList();
        AbstractList foo = (ArrayList) bar;
        List foo2 = (ArrayList) bar;
        double x = (double)3.0f;
    }

    <T> void test(T foo){}

    void test2()
    {
        Object o = null;
        test((TestInter)o);
    }

    public static Object[] array(List<?> l, Class type){
        return l.toArray((Object[]) Array.newInstance(type, l.size()));
    }
}
