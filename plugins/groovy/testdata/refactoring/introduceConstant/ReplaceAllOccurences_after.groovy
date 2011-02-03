class A {
    public static final String CONST = "a"

    def foo() {
    print <selection>CONST</selection>
  }

  def bar() {
    def CONST = 2
    print A.CONST
  }
}