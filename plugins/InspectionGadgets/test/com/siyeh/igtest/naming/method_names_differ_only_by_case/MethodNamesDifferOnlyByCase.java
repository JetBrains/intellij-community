package com.siyeh.igtest.naming.method_names_differ_only_by_case;

public class MethodNamesDifferOnlyByCase extends X
{
    public void <warning descr="Method name 'fooBar' and method name 'fooBAr' differ only by case">fooBar</warning>()
    {

    }

    public void <warning descr="Method name 'fooBAr' and method name 'fooBar' differ only by case">fooBAr</warning>()
    {

    }

  @Override
  void xx() {
    super.xx();
  }

  public int <warning descr="Method name 'hashcode' and method name 'hashCode' differ only by case">hashcode</warning>() {
    return 0;
  }

  public void f0() {}
  public void fo() {}
}
class X {

  void <warning descr="Method name 'xx' and method name 'xX' differ only by case">xx</warning>() {}
  void <warning descr="Method name 'xX' and method name 'xx' differ only by case">xX</warning>() {}

  @SuppressWarnings("MisspelledToString")
  public String tostring() {
    return "";
  }
}
