def foo() {
  String foo = 'used';
  try {
    thrower()
    <warning descr="Assignment is not used">foo</warning> = 'unused'
    foo = 'used'
    thrower()
  }
  finally {
    print foo
  }
}
