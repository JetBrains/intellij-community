class Test {
  String test(String a) {
    <caret>if(a == null) return null;
    return a;
  }
}