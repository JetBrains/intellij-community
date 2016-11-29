class PolyadicExpression {
  void m(Object arg) {
    if (arg instanceof String /*1*/ && /*2*/ arg.equals(arg)) {
      System.out.println("warning");
    }
  }
}