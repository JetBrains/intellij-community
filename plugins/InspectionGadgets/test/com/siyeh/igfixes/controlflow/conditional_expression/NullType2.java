class ConvertToIf {

  void test(String s) {
    foo(s == null ? null : <caret>InvalidClass.invalidMethod(s));
  }

  void foo(String s) {}
  void foo(boolean b) {}
}
