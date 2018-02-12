class PolyadicExpression {
  void m(Object arg) {
    if (arg != null<caret> /*1*/ && /*2*/ arg instanceof String && arg.equals(arg)) {
      System.out.println("warning");
    }
  }
}