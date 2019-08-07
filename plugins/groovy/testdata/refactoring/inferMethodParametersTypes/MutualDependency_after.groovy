def <T0 extends Comparable<U0>, U0 extends Comparable<T0>> int bar(T0 a, U0 b) {
  a.compareTo(b)
  b.compareTo(a)
}

bar(1, 1)
bar('a', 'b')