class T {
  static boolean same(String t, String s) {
    return s != null && s.<caret>equals(t + "a");
  }
}