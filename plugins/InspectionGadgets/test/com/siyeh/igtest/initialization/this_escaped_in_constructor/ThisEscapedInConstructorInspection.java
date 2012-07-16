package com.siyeh.igtest.initialization.this_escaped_in_constructor;

import java.util.ArrayList;
import java.util.List;

public class ThisEscapedInConstructorInspection{
    private boolean foo = Testing.foo(ThisEscapedInConstructorInspection.this, this);

    {
        final NoEscape escape = new NoEscape(this); // safe
        new NoEscape(null).field = this; // safe
        System.out.println(this);
    }

    public ThisEscapedInConstructorInspection(){
        super();
        final List list = new ArrayList(3);
        list.add(this); //escape
        new Testing().boom = this; // escape
    }

    private class NoEscape {
        Object field;

        NoEscape(Object o) {
        }
    }
}
class Testing
{
  Object boom;

  public static boolean  foo(Object val)
  {
    return true;
  }

  public static boolean foo(Object o1, Object o2) {
    return false;
  }
}
