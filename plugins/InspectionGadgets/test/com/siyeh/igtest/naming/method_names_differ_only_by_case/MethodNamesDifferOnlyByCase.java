package com.siyeh.igtest.naming.method_names_differ_only_by_case;

public class MethodNamesDifferOnlyByCase extends X
{
    public void fooBar()
    {

    }

    public void fooBAr()
    {

    }

  @Override
  void xx() {
    super.xx();
  }

  public int hashcode() {
    return 0;
  }

  public void f0() {}
  public void fo() {}
}
class X {

  void xx() {}
  void xX() {}
}
