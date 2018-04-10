class Nested {
  public boolean test(String s) {
    if(s != null) {
      int i = Integer.parseInt(s);
      i<caret>f(i > 0) {
        return true;
      }
    }
    return false;
  }
}