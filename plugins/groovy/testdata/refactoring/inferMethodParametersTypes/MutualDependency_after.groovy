def <Y0 extends Comparable<U0>, U0 extends Comparable<Y0>> Object bar(Y0 a, U0 b) {
  a.compareTo(b)
  b.compareTo(a)
}

bar(1, 1)
bar('a', 'b')