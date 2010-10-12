class A {
  public var a: Integer;
}

function foo(value: Integer) {
  A {
    a: value
  }
}

foo(3).<ref>a