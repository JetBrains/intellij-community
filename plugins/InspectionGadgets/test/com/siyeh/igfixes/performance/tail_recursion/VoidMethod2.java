class Test {
  void foo(int n, int acc) {
    if (n % 2 == 0) {
      System.out.println(acc);
    } else if (n % 3 == 0) {
      foo(n - 1, acc + n);
    } else if (n % 5 == 0) {
      foo(n - 1, acc + n);
      return;
    } else if (n % 7 == 0) {
      System.out.println(acc);
      return;
    }
    foo<caret>(n - 1, acc + n);
  }
}