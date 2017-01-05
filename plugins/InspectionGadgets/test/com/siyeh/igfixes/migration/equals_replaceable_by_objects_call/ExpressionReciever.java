class T {
  static boolean same(String t, String s) {
    return (t + "a").<caret>equals(s);
  }
}