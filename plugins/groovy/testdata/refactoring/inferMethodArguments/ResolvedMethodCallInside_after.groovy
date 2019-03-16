def <T> void fo<caret>o(List<T> a, T b) {
  a.add(b)
}

foo([1, 2, 3], 4)

foo(['a', 'b', 'c'], 'd')
