package com.siyeh.igtest.initialization;

import java.util.ArrayList;
import java.util.List;

class Test
{
    public static boolean  foo(Object val)
    {
        return true;
    }
}

public class ThisEscapedInConstructorInspection{
    private boolean foo = Test.foo(this);

    {
        System.out.println(this);
    }

    public ThisEscapedInConstructorInspection(){
        super();
        final List list = new ArrayList(3);
        list.add(this);
    }
}
