package com.siyeh.igtest.visibility.method_overloads_parent_method;

class MethodOverloadsParentMethod extends Parent {

  void foo(String s) {}

  public void equals(Integer s) {}

  String bla(int i) {
    return null;
  }
}
class Parent {

  public void equals(String s) {}

  @Override
  public boolean equals(Object obj) {
    return super.equals(obj);
  }

  void foo(Object o) {}

  Object bla(double d) {
    return null;
  }
}

class DefaultMethod {
  public static interface IFoo {
    default void foo(int i) {
      System.out.println("IFoo.foo(int): " + i);
    }

    void bar(int i);
    
  }

  public static class Foo implements IFoo {
    public void foo(long l) {
      System.out.println("Foo.foo(long): " + l);
    }

    public void bar(long l) {
      System.out.println("Foo.bar(long): " + l);
    }
  }
}