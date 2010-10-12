function foo(a: Integer) {
  if (a <= 1) {
    return 1
  }
  return fo<ref>o(a - 1) * a
}