package com.siyeh.igtest.style.unnecessarily_qualified_inner_class_access;
import java.util.Map;
@Y(Test.X.class)
public class Test<T> {

    public Test(int i) {
        Map.Entry entry;
    }

    public Test() {
        final String test = Test.Inner.TEST;
    }
    public static class Inner {
        public static final String TEST = "test";
    }

    void foo() {
        Test<String>.X x; // no warning here, because generic parameter is needed
        Test.Y<String> y;
    }

    class X {
        T t;
    }

    static class Y<T> {
        T t;
    }
}
@interface Y {
    Class value();
}

class Foo extends PresenterWidget<Foo.Bar> { //no warning as Bar here is not accessible
    interface Bar extends View { }
}

interface View {}
class PresenterWidget<T>{}

class MultipleInheritance {
  interface I1 {
    interface V {
    }

    String FOO = "";
  }

  interface I2 {
    interface V {
    }

    String FOO = "";
  }

  static class C implements I1, I2 {
    public I1.V foo() {
      System.out.println(I1.FOO);
      return null;
    }
  }
}