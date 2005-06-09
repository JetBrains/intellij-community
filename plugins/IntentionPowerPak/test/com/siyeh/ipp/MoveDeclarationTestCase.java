package com.siyeh.ipp;

public class MoveDeclarationTestCase {
    public int foo()
    {
        @Deprecated int y;
        System.out.println("bar");
        y = 3;
        y = 2;
        return y;
    }
    public void bar()
    {
        int x;
        System.out.println("x =");
        System.out.println(x);
    }

    public void baz()
    {
        int x;
        switch(foo())
        {
           case 2:
               x = 2;
               System.out.println(x);
               break;
           case 3:
               x = 5;
               System.out.println(x+6);
        }
    }
}
