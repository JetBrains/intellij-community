class A {
  boolean implicationLeftOperand(boolean a, boolean b) {
    return a ==> implicationLeftOperand(a, b)
  }

  boolean <warning descr="'implicationRightOperand' recurses infinitely, and can only complete by throwing an exception">implicationRightOperand</warning>(boolean a, boolean b) {
    return implicationRightOperand(a, b) ==> a
  }
}