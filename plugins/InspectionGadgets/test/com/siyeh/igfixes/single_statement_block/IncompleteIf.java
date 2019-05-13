class X {
  int f(int[] a) {
    for (int a : <caret>as) {
      if (true)
    }
    return 1;
  }
}