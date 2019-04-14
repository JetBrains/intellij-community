def <U extends Number, T0 extends U> voi<caret>d f(List<U> a, T0 b) {
  a.add(b)
}

f([1, 2, 3], 4)

