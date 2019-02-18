class Test {
  String test(boolean a, boolean b, boolean c, boolean d, boolean e) {
    return e || !a && (b <caret>|| c) || d ? "a" : "b";
  }
}