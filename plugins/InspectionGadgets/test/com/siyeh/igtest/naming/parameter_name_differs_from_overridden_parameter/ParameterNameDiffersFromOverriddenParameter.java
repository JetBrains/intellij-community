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