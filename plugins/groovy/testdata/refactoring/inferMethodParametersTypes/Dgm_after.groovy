def <T extends List<Integer>> List<T> foo(List<T> a) {
  a.each {
    it.first()
  }
}

foo(null as List<List<Integer>>)