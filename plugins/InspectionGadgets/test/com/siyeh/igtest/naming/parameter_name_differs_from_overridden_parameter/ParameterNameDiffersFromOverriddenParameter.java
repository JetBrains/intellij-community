class Super {
  Super() {
  }

  Super(int a, int b) {
  }

  void overloaded(String <warning descr="Parameter name 'str' is different from parameter 'boo' in the overloaded method">str</warning>) {
    overloaded(str, 0);
  }

  void overloaded(String boo, int start) {

  }
}

class Sub extends Super {
  Sub(int <warning descr="Parameter name 'c' is different from parameter 'a' in the super constructor">c</warning>, int b) {
    super(c, b);
  }

  Sub(int a, int b, int c) {
  }

  @Override
  void overloaded(String <warning descr="Parameter name 'string' is different from parameter 'str' in the super method">string</warning>) {
  }
}

class A<T> {
  A(T name, int age) {}
  A() {}
}

class B<T> extends A<T> {
  B(T <warning descr="Parameter name 'street' is different from parameter 'name' in the super constructor">street</warning>,
    int <warning descr="Parameter name 'number' is different from parameter 'age' in the super constructor">number</warning>) {
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

  F(String <warning descr="Parameter name 'text' is different from parameter 'name' in the overloaded constructor">text</warning>, int age) {
    this(text, age, 100);
  }
}

class G {
  G(String name, int age) {}
}

class H extends G {
  H(Object street, int <warning descr="Parameter name 'number' is different from parameter 'age' in the super constructor">number</warning>) {
    super(street.toString(), number);
  }
}