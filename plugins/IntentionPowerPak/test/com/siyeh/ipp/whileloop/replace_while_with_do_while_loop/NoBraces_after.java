class NoBraces {
  void m() {
      if (b()) {
          do System.out.println();
          while (b());
      }
  }

  boolean b() {
    return true;
  }
}