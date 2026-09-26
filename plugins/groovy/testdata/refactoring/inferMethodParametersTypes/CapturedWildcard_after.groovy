void foo(List<? super Integer> a) {
  a.add(1)
}

def bar(List<? super Integer> l) {
  foo(l)
}
