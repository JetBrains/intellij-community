class Foo {}
class FooImpl extends Foo {
  public static void foo(){}
  <caret>
}
class U {
  public static void main(String[] args) {
    FooImpl.foo();
  }
}
