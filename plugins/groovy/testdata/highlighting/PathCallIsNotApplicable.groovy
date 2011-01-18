class IntCategory {
  def call(Integer i, int q) {
    return {
      String s -> s + q;
    }
  }
}

use(IntCategory) {
  print 2<warning descr="'call' in 'IntCategory' cannot be applied to '(java.lang.Integer)'">(3)</warning><warning descr="'2(3)' cannot be applied to '(java.lang.String, java.lang.String)'">("a", "b")</warning>
}