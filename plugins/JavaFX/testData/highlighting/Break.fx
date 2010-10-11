function foo() {
  var a = 13 * 7;
  {
    <error descr="Break outside loop">break</error>;
  }
  while (true) {
    if (a > 0) {
      break;
    }
  }
  for (x in [1..10]) {
    break;
  }
}
