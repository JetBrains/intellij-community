class Test {
  boolean test(int x, int y) {
      while (true) {
          i<caret>f (x >= 10) return false;
          if (y > 10) return true;
          y = y % 2;
          x = x - 1;
      }
  }
}