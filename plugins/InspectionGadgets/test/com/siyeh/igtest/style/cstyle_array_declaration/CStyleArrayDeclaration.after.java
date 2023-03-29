package com.siyeh.igtest.style;

import java.lang.annotation.*;

public class CStyleArrayDeclaration
{
    private int[] m_foo;
    private int[] m_bar;

    public CStyleArrayDeclaration(int[] bar, int[] foo)
    {
        m_bar = bar;
        m_foo = foo;
        for(int i = 0; i < bar.length; i++)
        {
            m_foo[i] = m_bar[i];
        }

    }

    public void foo()
    {
        final int[] foo = new int[3];
        final int[] bar = new int[3];

        for(int i = 0; i < bar.length; i++)
        {
            foo[i] = bar[i];
        }
    }

    public void bar(int[] foo, int[] bar)
    {

    }

    String[] ohGod(String[] a) {
        return a;
    }

    record Record(int x[]) {
    }

    int[][] methodWithoutBody()


    void annotation() {
      String @Anno [] split = null;
    }

    @Target(ElementType.TYPE_USE)
    public @interface Anno {}

    public /*1*/ /*2*/ Integer @A(3) [] @A(4) [] @A(1) [] @A(2) [] test5()  /*3*/  /*4*/ {
      return null;
    }
    String @A(3) [] @A(4) [] @A(1) /*1*/[] @A(2) /*2*/[] string  /*3*/  /*4*/;

    @Target(ElementType.TYPE_USE)
    @interface A {
      int value();
    }
}
