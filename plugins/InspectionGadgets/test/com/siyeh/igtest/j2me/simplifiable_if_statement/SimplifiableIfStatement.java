package com.siyeh.igtest.j2me.simplifiable_if_statement;

public class SimplifiableIfStatement {
    public void foo() {
        boolean a = bar();
        boolean b = bar();
        final boolean i;
        <warning descr="'if' statement can be replaced with 'i = !a || b;'">if</warning> (a) {
            i = b;
        } else {
            i = true;
        }
        final boolean j;
        <warning descr="'if' statement can be replaced with 'j = a || b;'">if</warning> (a) {
            j = true;
        } else {
            j = b;
        }
        final boolean k;
        <warning descr="'if' statement can be replaced with 'k = a && b;'">if</warning> (a) {
            k = b;
        } else {
            k = false;
        }
        final boolean l;
        <warning descr="'if' statement can be replaced with 'l = !a && b;'">if</warning> (a) {
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
        <warning descr="'if' statement can be replaced with 'return !a || b;'">if</warning> (a) {
            return b;
        } else {
            return true;
        }
    }

    public boolean foo2() {
        boolean a = bar();
        boolean b = bar();
        <warning descr="'if' statement can be replaced with 'return a || b;'">if</warning> (a) {
            return true;
        } else {
            return b;
        }
    }

    public boolean foo3() {
        boolean a = bar();
        boolean b = bar();
        <warning descr="'if' statement can be replaced with 'return !a && b;'">if</warning> (a) {
            return false;
        } else {
            return b;
        }
    }

    public boolean foo4() {
        boolean a = bar();
        boolean b = bar();
        <warning descr="'if' statement can be replaced with 'return a && b;'">if</warning> (a) {
            return b;
        } else {
            return false;
        }
    }

  public static boolean original(boolean a, boolean b, boolean c, boolean d) {

    <warning descr="'if' statement can be replaced with 'return (a || b) && (c || d);'">if</warning> (!(a || b)) {
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
    if (i == 3) {
      a = null;
    } else {
      a = false;
    }
  }

  boolean m(boolean b1, boolean b2, boolean b3, boolean b4, boolean i) {
    <warning descr="'if' statement can be replaced with 'return b1!=b2==b3!=b4 && (i = true);'">if</warning> (b1 == b2 == b3 == b4) {
      return false;
    }
    return i = true;
  }

  boolean test( boolean b1, boolean b2, boolean b3 )
  {
    <warning descr="'if' statement can be replaced with 'return (!b1 || !b2) && b3;'">if</warning> (b1 && b2)
      return false;
    return b3;
  }
}