package com.siyeh.igtest.bugs;

import java.util.HashMap;
import java.util.Map;

public class ObjectEqualsInspection
{
    public ObjectEqualsInspection()
    {
    }

    public void fooBar()
    {
        final Map map1 = new HashMap(5);
        final Map map2 = new HashMap(5);
        if (map1 == map2)
        {

        }
        if (null == map2)
        {

        }
        if (map1 == null)
        {

        }
    }
    
    public void fooBarEnum()
    {
        final MyEnum enum1 = MyEnum.foo;
        final MyEnum enum2 = MyEnum.bar;
        if (enum1 == enum2)
        {

        }
    }

    public void fooBarClass()
    {
        final Class class1 = String.class;
        final Class class2 = Object.class;
        if (class1 == class2)
        {

        }
        if(char.class == char.class)
        {

        }
    }
}
