package com.siyeh.igtest.encapsulation.protected_inner_class;

public class ProtectedInnerClass
{
  protected class <warning descr="Protected nested class 'Barangus'">Barangus</warning>
  {

    public Barangus(int val)
    {
      this.val = val;
    }

    int val = -1;
  }

  protected enum E {
    ONE, TWO
  }

  protected interface I {

    void foo();
  }

}
