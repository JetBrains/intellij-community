package com.siyeh.igtest.visibility.method_overloads_parent_method;

class MethodOverloadsParentMethod extends Parent {

  void <warning descr="Method 'foo()' overloads a compatible method of a superclass, when overriding might have been intended">foo</warning>(String s) {}

  public void <warning descr="Method 'equals()' overloads a compatible method of a superclass, when overriding might have been intended">equals</warning>(Integer s) {}

  String <warning descr="Method 'bla()' overloads a compatible method of a superclass, when overriding might have been intended">bla</warning>(int i) {
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

  <error descr="Class 'Foo' must either be declared abstract or implement abstract method 'bar(int)' in 'IFoo'">public static class Foo implements IFoo</error> {
    public void <warning descr="Method 'foo()' overloads a compatible method of a superclass, when overriding might have been intended">foo</warning>(long l) {
      System.out.println("Foo.foo(long): " + l);
    }

    public void bar(long l) {
      System.out.println("Foo.bar(long): " + l);
    }
  }
}