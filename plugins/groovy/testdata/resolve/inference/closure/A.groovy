class A {
  static def fact = {
    int i ->
    if (i > 1) {
      return call(i - 1)
    } else {
      return 1
    }

  }

  public static void main(String[] args) {
    def var = A.fact(5)
    println(<ref>var)
  }
}