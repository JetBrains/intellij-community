// "Annotate parameter 'str' as '@NonNls'" "true"
class X {
  void test(String str) {
    String result = "foo" +<caret> str;  
  }
}

