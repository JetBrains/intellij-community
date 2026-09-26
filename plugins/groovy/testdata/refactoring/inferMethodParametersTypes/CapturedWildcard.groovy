void foo(a) {
  a.add(1)
}

def bar(List<? super Integer> l) {
  foo(l)
}
