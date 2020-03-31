def foo(cl) {
  (null as C<Integer>).m(cl)
}

class C<T> {
  def m(@DelegatesTo(type = "T") Closure cl) {}
}

foo {
  doubleValue()
}