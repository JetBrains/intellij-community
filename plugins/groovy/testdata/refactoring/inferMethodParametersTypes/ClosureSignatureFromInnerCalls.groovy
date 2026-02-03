def foo(cl) {
  cl(1, 2)
}

def m(Closure cl) {
  foo(cl)
}