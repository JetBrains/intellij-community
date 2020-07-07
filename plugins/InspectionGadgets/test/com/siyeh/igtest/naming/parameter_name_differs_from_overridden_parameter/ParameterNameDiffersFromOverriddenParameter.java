class Super {
  Super() {
  }

  Super(int a, int b) {
  }

  void overloaded(String str) {
  }
}

class Sub extends Super {
  Sub(int <warning descr="Parameter name 'c' is different from parameter 'a' overridden">c</warning>, int b) {
    super(c, b);
  }

  Sub(int a, int b, int c) {
  }

  @Override
  void overloaded(String <warning descr="Parameter name 'string' is different from parameter 'str' overridden">string</warning>) {
  }
}

class A<T> {
  A(T name, int age) {}
  A() {}
}

class B<T> extends A<T> {
  B(T <warning descr="Parameter name 'street' is different from parameter 'name' overridden">street</warning>,
    int <warning descr="Parameter name 'number' is different from parameter 'age' overridden">number</warning>) {
    super(street, number);
  }
}

class C {
  C(String name, int age) {}
  C() {}
}

class D extends C {
  D(String street, int number) {
    super();
  }
}

class E {
  E(String name, int age) {}
}

class F extends E {
  F(String name, int age, int weight) {
    super(name, age);
  }
}