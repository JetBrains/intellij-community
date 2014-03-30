class NoBraces {
  void m() {
    while<caret>(b()) System.out.println();
  }

  boolean b() {
    return true;
  }
}