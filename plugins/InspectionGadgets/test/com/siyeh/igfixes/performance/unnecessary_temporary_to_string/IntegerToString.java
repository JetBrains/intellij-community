class Test {
  void test(int[] i) {
    System.out.println(new Integer(i[/*zero*/0])<caret>.toString(/*foo*/));
  }
}