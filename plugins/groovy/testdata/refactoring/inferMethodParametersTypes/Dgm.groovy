def foo(a) {
  a.each {
    it.first()
  }
}

foo(null as List<List<Integer>>)