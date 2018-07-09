class T {
  static boolean same(String t, String s) {
    return s//c1
           != null && s.<caret>equals(t + "a");
  }
}