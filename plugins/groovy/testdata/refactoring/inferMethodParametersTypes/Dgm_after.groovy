List<List<Integer>> foo(List<List<Integer>> a) {
  a.each {
    it.first()
  }
}

foo(null as List<List<Integer>>)