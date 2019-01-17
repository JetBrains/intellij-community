class Test {
  String test(String a) {
    <caret>if(null != a) return a;
    return null;
  }
}