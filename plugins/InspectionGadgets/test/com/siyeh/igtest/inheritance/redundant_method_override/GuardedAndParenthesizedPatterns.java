class Main {
  void foo1(Object obj) {
    switch (obj) {
      case ((((((String s)))))) && (((((s.isEmpty()))))) -> System.out.println(s);
      case default -> System.out.println(42);
    }
    if (obj instanceof ((((((String s)))))) && (((((s.isEmpty())))))) {
      System.out.println(s);
    }
  }
}

class Foo extends Main {
  @Override
  void <warning descr="Method 'foo1()' is identical to its super method">foo1</warning>(Object o) {
    switch (o) {
      case String str && str.isEmpty() -> System.out.println(str);
      default -> System.out.println(42);
    }
    if (o instanceof String str && str.isEmpty()) {
      System.out.println(str);
    }
  }
}