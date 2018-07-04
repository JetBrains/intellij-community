class Nested {
  public boolean test(String s) {
    if(s != null) {
      int i = Integer.parseInt(s);
        return i > 0;
    }
    return false;
  }
}