class A {
  void method(String m) {}

  {
    method<warning descr="'method' in 'A' cannot be applied to '([:])'">([:])</warning>
  }
}