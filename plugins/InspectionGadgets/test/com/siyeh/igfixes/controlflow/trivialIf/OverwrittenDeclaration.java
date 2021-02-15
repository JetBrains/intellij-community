class X {
  boolean test(int x) {
    boolean result = false;
    <caret>if (x == 0) result = true;
    return result;
  }
}