def <T extends Comparable<U>, U extends Comparable<T>> int bar(T a, U b) {
  a.compareTo(b)
  b.compareTo(a)
}

bar(1, 1)
bar('a', 'b')