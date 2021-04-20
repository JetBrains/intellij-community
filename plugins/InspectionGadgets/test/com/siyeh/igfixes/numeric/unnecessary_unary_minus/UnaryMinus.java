class Test {
  void test(int a, int b) {
    int i = - - - a;
    i = - -4;
    i = -a;
    i = a + <caret>-8;
    i = a + -getResult();
    String s = "Hello World" + -1 + "asdf";
    byte b = 2;
    test(- - b);
    char c = 3;
    test(- -c);
  }

  void test() {
    byte i = 1;
    test(+i);
  }

  void test(short s) {
  }

  void test(int i) {
  }
}