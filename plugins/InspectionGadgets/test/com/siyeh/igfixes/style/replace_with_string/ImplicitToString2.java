class ImplicitToString2 {

  void testStringBuilder() {
    System.out.println("Hello " + new StringBuilder<caret>().append("World!"));
  }
}