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
        if (map1 <warning descr="Object values are compared using '==', not 'equals()'">==</warning> map2)
        {

        }
        if (null == map2)
        {

        }
        if (map1 == null)
        {

        }
        if (map1 ==<error descr="Expression expected"> </error>) {}
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

class TypeParameterWithEnumBound<E extends Enum<E>> {
  public void checkEnums(E a, E b) {
    if (a == b) {
      return;
    }
  }
}