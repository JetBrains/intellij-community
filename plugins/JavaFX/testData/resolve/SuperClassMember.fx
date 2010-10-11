class A {
  public-init def a: Integer;
}

class B extends A {
}

class C extends B {
  public function foo() {
    <ref>a = 3;
  }
}