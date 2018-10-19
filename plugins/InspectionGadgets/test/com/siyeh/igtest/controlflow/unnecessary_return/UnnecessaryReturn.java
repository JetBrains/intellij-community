package com.siyeh.igtest.controlflow.unnecessary_return;

import java.util.concurrent.Callable;

public class UnnecessaryReturn
{

    public UnnecessaryReturn()
    {
        <warning descr="'return' is unnecessary as the last statement in a constructor">return</warning>;
    }

    public void foo()
    {
        <warning descr="'return' is unnecessary as the last statement in a 'void' method">return</warning>;
    }
    public void foo2()
    {
        {
            {
                <warning descr="'return' is unnecessary as the last statement in a 'void' method">return</warning>;
            }
        }
    }

    public void bar()
    {
        if(true)
        {
            <warning descr="'return' is unnecessary as the last statement in a 'void' method">return</warning>;
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
    <warning descr="'return' is unnecessary as the last statement in a constructor">return</warning>;
  }

  public void m1() {
    <warning descr="'return' is unnecessary as the last statement in a 'void' method">return</warning>;
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
      <warning descr="'return' is unnecessary as the last statement in a 'void' method">return</warning>;
    }
  }

  public void m4(boolean f) {
    if (f) {
      System.out.println("m4()");
      return;
    }
    else {
      <warning descr="'return' is unnecessary as the last statement in a 'void' method">return</warning>;
    }
  }

  public void m5() {
    while (true) {
      System.out.println("m5()");
      return;
    }
  }

  public void lambda() {
    Runnable r = () -> { <warning descr="'return' is unnecessary as the last statement in a 'void' method">return</warning>; };
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
        return<EOLError descr="';' expected"></EOLError>
      }
    };
    
    A a1 = () -> {
      return<EOLError descr="';' expected"></EOLError>
    };
  }
}