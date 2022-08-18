class Test {
  void test(int[] i) {
    System.out.println(new <caret>Integer(i[/*zero*/0]).toString(/*foo*/));
  }
}