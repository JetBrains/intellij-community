def <T> int foo(Comparable<T> a, T b) {
  a <=> b
}

foo 1, 2
foo 'a', 'b'