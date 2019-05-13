class A {
  def foo() {
    print Other.CONST<caret>
  }
}

class Other {

    protected static final CONST = "abc"
}