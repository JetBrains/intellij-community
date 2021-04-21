class UnaryMinus {
  static void test(int a, int m) {
    int i = <warning descr="Unnecessary unary '-' operator">-</warning>(<warning descr="Unnecessary unary '-' operator">-</warning> <warning descr="Unnecessary unary '-' operator">-</warning> a);
    i = <warning descr="Unnecessary unary '-' operator">-</warning> <warning descr="Unnecessary unary '-' operator">-</warning> <warning descr="Unnecessary unary '-' operator">-</warning> <warning descr="Unnecessary unary '-' operator">-</warning> <warning descr="Unnecessary unary '-' operator">-</warning>4;
    i = <warning descr="Unnecessary unary '-' operator">-</warning> <warning descr="Unnecessary unary '-' operator">-</warning> <warning descr="Unnecessary unary '-' operator">-</warning> <warning descr="Unnecessary unary '-' operator">-</warning>a;
    i = -a;
    i = a + <warning descr="Unnecessary unary '-' operator">-</warning>8;
    i = <warning descr="Unnecessary unary '-' operator">-</warning> <warning descr="Unnecessary unary '-' operator">-</warning> getResult();
    i = <warning descr="Unnecessary unary '-' operator">-</warning> (/*fsdf*/(<warning descr="Unnecessary unary '-' operator">-</warning> a));
    i = <warning descr="Unnecessary unary '-' operator">-</warning> <warning descr="Unnecessary unary '-' operator">-</warning> /*fsdf*/a;
    i = <warning descr="Unnecessary unary '-' operator">-</warning> /*fsdf*/ <warning descr="Unnecessary unary '-' operator">-</warning>a;
    i = /*fsdf*/ <warning descr="Unnecessary unary '-' operator">-</warning> <warning descr="Unnecessary unary '-' operator">-</warning>a;
    String s = "Hello World" + -1 + "asdf";
    byte b = 2;
    test(- - b);
    char c = 3;
    test(<warning descr="Unnecessary unary '-' operator">-</warning> <warning descr="Unnecessary unary '-' operator">-</warning>c);
  }

  static void test(byte i) {
  }

  static void test(int i) {
  }

  static int getResult() {
    return 0;
  }
}