package com.siyeh.igtest.controlflow.unnecessary_return;

import java.util.concurrent.Callable;

public class UnnecessaryReturn
{

    public UnnecessaryReturn()
    {
        return;
    }

    public void foo()
    {
        return;
    }
    public void foo2()
    {
        {
            {
                return;
            }
        }
    }

    public void bar()
    {
        if(true)
        {
            return;
        }
    }

    public void barzoom()
    {
        while(true)
        {
            return;
        }
    }

}
class C {
  public C() {
    return;
  }

  public void m1() {
    return;
  }

  public boolean m2() {
    return true;
  }

  public void m3(boolean f) {
    if (!f) {
      return;
    }
    System.out.println("m3()");
    if (f) {
      return;
    }
  }

  public void m4(boolean f) {
    if (f) {
      System.out.println("m4()");
      return;
    }
    else {
      return;
    }
  }

  public void m5() {
    while (true) {
      System.out.println("m5()");
      return;
    }
  }

  public void lambda() {
    Runnable r = () -> { return; };
    System.out.println(r);

    Callable<Integer> c = () -> { return 42; };
    System.out.println(c);
  }

  void m5(boolean a) {
    if (a) return;
    else {
      System.out.println();
    }
  }
}

class Incomplete {
  interface A {
    Void m();
  }
  
  {
    A a = new A() {
      public Void m() {
        return
      }
    };
    
    A a1 = () -> {
      return
    };
  }
}