class IntCategory {
  static def call(Integer i, int q) {
    return {
      String s -> s + q;
    }
  }
}

use(IntCategory) {
  print 2(3)<warning descr="'call' cannot be applied to '(java.lang.String, java.lang.String)'">("a", "b")</warning>
}