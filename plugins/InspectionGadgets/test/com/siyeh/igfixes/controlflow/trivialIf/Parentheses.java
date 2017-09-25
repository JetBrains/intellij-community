class X {
  boolean test(String s1, String s2) {
    if<caret>(s1 != null && !s1.isEmpty() || s2 != null && !s2.isEmpty()) return false;
    return true;
  }
}