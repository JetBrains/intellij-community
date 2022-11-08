record Point(double x, double y) {
}

record Rect(Point point1, Point point2) {
}

record A(double x, double y) {
}

class Foo {
  void test1(Object obj) {
    if (obj instanceof Point(double x, double y)) {
      System.out.println(x);
      System.out.println(obj);
      System.out.println(y);
    }
  }

  void test2(Object obj) {
    if (obj instanceof Point(double x, double y) point) {
      System.out.println(obj);
      System.out.println(x);
      System.out.println(point);
      System.out.println(y);
    }
  }

  void test3(Object obj) {
    if (obj instanceof Point(double x, double y) ) {}
  }

  void test4(Object obj) {
    if (obj instanceof Point(double x, double y) point) {}
  }

  void test5(Object obj) {
    if (obj instanceof Rect(Point(double x1, double y1) point1, Point(double x2, double y2) point2) rect) {
      System.out.println(x1);
      System.out.println(x2);
      System.out.println(point2);
      System.out.println(rect);
      System.out.println(y1);
      System.out.println(y2);
      System.out.println(point1);
    }
  }

  void test6(Object obj) {
    if (obj instanceof Point(double x, double y) point) {
    }
  }

  void test7(Object obj) {
    if (obj instanceof Point(double x, double y) point) {
    }
  }

  void test8(Object obj) {
    if (obj instanceof Point(double x, double y)) {
      System.out.println(x);
    }
  }

  void test9(Object obj) {
    if (obj instanceof String s && s.length() > 42) {
      System.out.println(s);
    }
  }

  void test10(Object obj) {
    if (obj instanceof String s && s.length() > 42) {
      System.out.println(s);
    }
  }

  void test11(Object obj) {
    if (obj instanceof Integer) {
      System.out.println(42);
    }
  }

  void test12(Object obj) {
    if (obj instanceof Integer) {
      System.out.println(42);
    }
  }
}

class Bar extends Foo {
  @Override
  void <warning descr="Method 'test1()' is identical to its super method">test1</warning>(Object o) {
    if (o instanceof Point(double a, double b)) {
      System.out.println(a);
      System.out.println(o);
      System.out.println(b);
    }
  }

  @Override
  void <warning descr="Method 'test2()' is identical to its super method">test2</warning>(Object o) {
    if (o instanceof Point(double a, double b) p) {
      System.out.println(o);
      System.out.println(a);
      System.out.println(p);
      System.out.println(b);
    }
  }

  @Override
  void test3(Object obj) {
    if (obj instanceof A(double x, double y) ) {}
  }

  @Override
  void test4(Object obj) {
    if (obj instanceof Point(double x, <error descr="Incompatible types. Found: 'int', required: 'double'">int y</error>) point) {}
  }

  @Override
  void <warning descr="Method 'test5()' is identical to its super method">test5</warning>(Object o) {
    if (o instanceof Rect(Point(double a1, double b1) p1, Point(double a2, double b2) p2) r) {
      System.out.println(a1);
      System.out.println(a2);
      System.out.println(p2);
      System.out.println(r);
      System.out.println(b1);
      System.out.println(b2);
      System.out.println(p1);
    }
  }

  @Override
  void test6(Object obj) {
    if (obj instanceof Point(double x, double y, <error descr="Incorrect number of nested patterns: expected 2 but found 3">double z)</error> point) {
    }
  }

  @Override
  void test7(Object obj) {
    if (obj instanceof Point(double x, double y)) {
    }
  }

  @Override
  void test8(Object obj) {
    if (obj instanceof Point(double x, double y)) {
      System.out.println(y);
    }
  }

  @Override
  void test9(Object obj) {
    if (obj instanceof CharSequence str && str.length() > 42) {
      System.out.println(str);
    }
  }

  @Override
  void test10(Object obj) {
    if (obj instanceof String str && str.length() > 0) {
      System.out.println(str);
    }
  }

  @Override
  void test11(Object obj) {
    if (obj instanceof String) {
      System.out.println(42);
    }
  }

  @Override
  void test12(Object obj) {
    if (obj instanceof Integer integer) {
      System.out.println(42);
    }
  }
}