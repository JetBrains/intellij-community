class PolyadicExpression {
  void m(Object arg) {
    if (arg instanceof String && arg != null<caret> && /* :-) */ arg.equals(arg)) {
      System.out.println("warning");
    }
  }
}