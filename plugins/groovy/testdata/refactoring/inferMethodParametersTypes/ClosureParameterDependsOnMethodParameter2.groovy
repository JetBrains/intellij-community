static void foo(self, closure ) {
  closure(self, 0)
}


foo(1, {a, ind -> println(a)})
foo('q', {a, ind -> println(a)})