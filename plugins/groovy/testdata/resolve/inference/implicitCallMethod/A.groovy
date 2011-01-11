class IntCategory {
  static def call(Integer o, Integer i1, Integer i2) {
    return "abc"
  }
}
boolean foo(int q) {
  use(IntCategory) {
    def q1 = q(1, 2)
    print q<ref>1
  }

  return false;
}