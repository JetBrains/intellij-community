class Test {
  String test(String s) {
    if (s != null) {
      String res = process(s);
        <caret>return res;
    }
    return null;
  }
  
  native String process(String s);
}