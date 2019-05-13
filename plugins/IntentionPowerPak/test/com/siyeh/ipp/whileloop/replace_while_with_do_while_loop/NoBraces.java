class NoBraces {
  void m() {
    while<caret>//after while
          (b(/*inside call*/)) //before body
    System.out.println();
  }

  boolean b() {
    return true;
  }
}