// "Add 'Locale.ENGLISH' argument" "true"
class X {
  void test() {
    String foo = "bar";
    String foo1 = foo.toUpperCa<caret>se();
  }
}