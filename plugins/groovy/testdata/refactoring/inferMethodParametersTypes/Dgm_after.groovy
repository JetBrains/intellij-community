def <U0 extends List<Integer>> List<U0> foo(List<U0> a) {
  a.each {
    it.first()
  }
}

foo(null as List<List<Integer>>)