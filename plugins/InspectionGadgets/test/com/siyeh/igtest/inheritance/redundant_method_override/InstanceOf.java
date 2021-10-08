class Foo {
  void test1(Object o) {
    if (((o instanceof ((String s)) && ((s.length() > 42))))) {
      System.out.println(s);
    }
  }

  void test2(Object o) {
    if (o instanceof String s && s.length() > 42) {
      System.out.println(s);
    }
  }

  void test3(Object o) {
    if (o instanceof String s && s.length() > 42) {
      System.out.println(s);
    }
  }
}

class Bar extends Foo {
  void <warning descr="Method 'test1()' is identical to its super method">test1</warning>(Object o) {
    if (o instanceof String str && str.length() > 42) {
      System.out.println(str);
    }
  }

  void test2(Object o) {
    if (o instanceof CharSequence str && str.length() > 42) {
      System.out.println(str);
    }
  }

  void test3(Object o) {
    if (o instanceof String str && str.length() > 0) {
      System.out.println(str);
    }
  }
}