class PolyadicExpression {
  void m(Object arg) {
    if (arg instanceof String /*1*/ && /*2*/ arg.equals(arg) && arg != null<caret>) {
      System.out.println("warning");
    }
  }
}