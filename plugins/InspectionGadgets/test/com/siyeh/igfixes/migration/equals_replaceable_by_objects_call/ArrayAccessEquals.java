class T {
  static boolean same(String[] t, String[] s, int i) {
    return s[i] != null && s[i].<caret>equals(t[i]);
  }
}