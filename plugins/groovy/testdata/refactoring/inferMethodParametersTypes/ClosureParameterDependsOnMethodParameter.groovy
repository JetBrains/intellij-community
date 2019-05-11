def void foo(c, s) {
  c(s)
}

foo({ it.toUpperCase()}, 's')