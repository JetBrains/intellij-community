def <F> boolean foo(Iterable<F> list, cl) {
  list.every(cl)
}

foo([1]) {it % 2 == 0}
foo(['q']) {it.reverse() == it}

