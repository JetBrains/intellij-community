class X {
  void f(int[] a) {
    if (a.length != 0) {
      System.out.println("no");
    } else {
      for (int i : a)
        System.out.println(i);
    }<caret>
  }
}