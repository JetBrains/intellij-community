def <T0 extends Comparable<V0>, V0 extends Comparable<T0>> Object bar(T0 a, V0 b) {
  a.compareTo(b)
  b.compareTo(a)
}

bar(1, 1)
bar('a', 'b')