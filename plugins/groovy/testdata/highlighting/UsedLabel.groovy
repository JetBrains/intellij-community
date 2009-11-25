oo:
for (s in ['a', 'b']) {
  oo1:
  for (s1 in ['a', 'b']) {
    <warning descr="Label 'oo' is already in use">oo</warning>:
    for (s2 in ['a', 'b']) {
    }
  }
}