oo:
for (s in ['a', 'b']) {
  for (s1 in ['a', 'b']) {
    break <error descr="Undefined label 'o1o'">o1o</error>;
  }
}
