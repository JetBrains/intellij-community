oo:
for (s in ['a', 'b']) {
  oo1:
  for (s1 in ['a', 'b']) {
    <error descr="Label 'oo' is already in use">oo</error>:
    for (s2 in ['a', 'b']) {
    }
  }
}