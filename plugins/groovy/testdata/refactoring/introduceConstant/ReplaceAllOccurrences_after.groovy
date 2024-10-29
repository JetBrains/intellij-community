class A {
    public static final String CONST = "a"

    def foo() {
    print CONST<caret>
  }

  def bar() {
    def CONST = 2
    print A.CONST
  }
}