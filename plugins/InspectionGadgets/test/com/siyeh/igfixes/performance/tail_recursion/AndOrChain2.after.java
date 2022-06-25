class Test {
  boolean test(int x, int y) {
      while (true) {
          if (x >= 10) {
              return fa<caret>lse;
          }
          if (y > 10) {
              return true;
          }
          y = y % 2;
          x = x - 1;
      }
  }
}