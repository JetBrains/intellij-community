def foo() {
  switch (a) {
    case 1:
      print 1
      <warning descr="return is unnecessary as the last statement in a method with no return value">return</warning>
    case 2:
      print 2
      return
  }
}