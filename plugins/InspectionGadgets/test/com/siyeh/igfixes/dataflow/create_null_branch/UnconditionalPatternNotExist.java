class Test {
  final String s = null;

  int test3() {
    return switch (<caret>s) {
      case String s && s.length() <= 3 -> 1;
      case "abc" -> 2;
      case default -> 3;
    };
  }
}