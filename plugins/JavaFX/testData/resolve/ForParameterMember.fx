class A {
  var a: Integer;
}

function foo(a: A[]) {
  for (i in a) {
    i.<ref>a = 3
  }
}