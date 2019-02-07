class Test {
  String test(String s) {
    if (s != null) {
      s = process(s);
      <caret>if (s == null) return null;
    }
    return s;
  }
  
  native String process(String s);
}