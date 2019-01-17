class Test {
  String test(String s) {
    if (s != null) {
      String res = process(s);
        <caret>return res;
    } else {
      return "";
    }
  }
  
  native String process(String s);
}