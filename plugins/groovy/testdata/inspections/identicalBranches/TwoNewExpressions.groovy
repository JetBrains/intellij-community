class IfStatementWithIdenticalBranchesTest {
  void test(boolean b) {
    b ? new Foo() : new Bar()

    if (b) {
      new Foo()
    }
    else {
      new Bar()
    }
  }
}

class Foo {
  Foo() {}
}

class Bar {
  Bar() {}
}
