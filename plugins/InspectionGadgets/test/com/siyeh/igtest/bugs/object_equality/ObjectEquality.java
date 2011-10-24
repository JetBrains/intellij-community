package com.siyeh.igtest.bugs.object_equality;

import java.util.HashMap;
import java.util.Map;

public class ObjectEquality
{
    public ObjectEquality()
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
        if (map1 == ) {}
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
        final Class class2 = com.siyeh.igtest.bugs.Object.class;
        if (class1 == class2)
        {

        }
        if(char.class == char.class)
        {

        }
    }

  public void fooBarPrivate(X x, X y) {
    if (x == y) {

    }
  }

  enum MyEnum{
    foo, bar, baz;
  }

  class X {
    private X() {}
  }

}
