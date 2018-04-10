class Simple {

  void m(int i) {
    while<caret> (i > 0) {
      System.out.println(i);
      i--;
    }
  }
}