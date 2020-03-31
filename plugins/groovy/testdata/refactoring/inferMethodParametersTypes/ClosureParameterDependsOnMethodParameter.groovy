void foo(c, s) {
  c(s)
}

foo({ it.toUpperCase()}, 's')
foo( {it.doubleValue()}, 1)