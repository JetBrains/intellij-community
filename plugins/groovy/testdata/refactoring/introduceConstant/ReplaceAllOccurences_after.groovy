<caret>class A {
    public static final String CONST = "a"

    def foo() {
    print CONST
  }

  def bar() {
    def CONST = 2
    print A.CONST
  }
}