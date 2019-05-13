class Foo {
  
}

class FooImpl extends Foo {
  private void fo<caret>o(){}
  void bar() {
    foo();
  }
}
