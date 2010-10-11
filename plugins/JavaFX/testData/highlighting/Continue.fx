function foo() {
  var a = 13 * 7;
  {
    <error descr="Continue outside loop">continue</error>;
  }
  while (true) {
    if (a > 0) {
      continue;
    }
  }
  for (x in [1..10]) {
    continue;
  }
}
