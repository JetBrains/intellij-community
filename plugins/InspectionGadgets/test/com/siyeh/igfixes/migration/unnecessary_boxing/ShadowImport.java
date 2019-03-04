package foo.bar;

class Test {
  void test(String str) {
    int i = Integer.value<caret>Of(str);
  }
}

class Integer {
  static int valueOf(String str) {
    return 42;
  }
}