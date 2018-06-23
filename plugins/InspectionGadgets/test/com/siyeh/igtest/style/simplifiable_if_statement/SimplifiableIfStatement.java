package com.siyeh.igtest.j2me.simplifiable_if_statement;

public class SimplifiableIfStatement {
    public void foo() {
        boolean a = bar();
        boolean b = bar();
        final boolean i;
        <warning descr="If statement can be replaced with '||'">if</warning> (a) {
            i = b;
        } else {
            i = true;
        }
        final boolean j;
        <warning descr="If statement can be replaced with '||'">if</warning> (a) {
            j = true;
        } else {
            j = b;
        }
        final boolean k;
        <warning descr="If statement can be replaced with '&&'">if</warning> (a) {
            k = b;
        } else {
            k = false;
        }
        final boolean l;
        <warning descr="If statement can be replaced with '&&'">if</warning> (a) {
            l = false;
        } else {
            l = b;
        }
    }

    private boolean bar(){
        return true;
    }

    public boolean foo1() {
        boolean a = bar();
        boolean b = bar();
        <warning descr="If statement can be replaced with '||'">if</warning> (a) {
            return b;
        } else {
            return true;
        }
    }

    public boolean foo2() {
        boolean a = bar();
        boolean b = bar();
        <warning descr="If statement can be replaced with '||'">if</warning> (a) {
            return true;
        } else {
            return b;
        }
    }

    public boolean foo3() {
        boolean a = bar();
        boolean b = bar();
        <warning descr="If statement can be replaced with '&&'">if</warning> (a) {
            return false;
        } else {
            return b;
        }
    }

    public boolean foo4() {
        boolean a = bar();
        boolean b = bar();
        <warning descr="If statement can be replaced with '&&'">if</warning> (a) {
            return b;
        } else {
            return false;
        }
    }

  public static boolean original(boolean a, boolean b, boolean c, boolean d) {

    <warning descr="If statement can be replaced with '&&'">if</warning> (!(a || b)) {
      return false;
    }

    return c || d;
  }

  Boolean wrong1(int i) {
    if (i == 3) {
      return null;
    }
    return false;
  }

  void wrong2(int i) {
    Boolean a;
    <warning descr="If statement can be replaced with '?:'">if</warning> (i == 3) {
      a = null;
    } else {
      a = false;
    }
  }

  boolean m(boolean b1, boolean b2, boolean b3, boolean b4, boolean i) {
    <warning descr="If statement can be replaced with '&&'">if</warning> (b1 == b2 == b3 == b4) {
      return false;
    }
    return i = true;
  }

  boolean test( boolean b1, boolean b2, boolean b3 )
  {
    <warning descr="If statement can be replaced with '&&'">if</warning> (b1 && b2)
      return false;
    return b3;
  }

  boolean doSomething(int a, int b) {
    <warning descr="If statement can be replaced with '||'">if</warning>(a < 0) return true;
    return 0 > a || a > b;
  }

  boolean doSomethingRandom(int a, int b) {
    <warning descr="If statement can be replaced with '||'">if</warning>(Math.random() > 0.5) return true;
    return Math.random() > 0.5 || a > b;
  }

  boolean doSomething1(int a, int b) {
    <warning descr="If statement can be replaced with '||'">if</warning>(a < 0 || b < a) return true;
    return a > b;
  }

  boolean doSomething2(int a, int b) {
    <warning descr="If statement can be replaced with '&&'">if</warning>(a > b) {
      return b < a && a < 0;
    }
    return false;
  }

  void testMethod(int a, int b) {
      <warning descr="If statement can be replaced with '?:'">if</warning>(a > b) {
        System.out.println(a);
      } else {
        System.out.println(b);
      }
  }
}