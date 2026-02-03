def foo(list) {
  x(list)
}

def x(List<? super Integer> ls) {}

foo([1] as List<Object>)
foo([1] as List<Number>)