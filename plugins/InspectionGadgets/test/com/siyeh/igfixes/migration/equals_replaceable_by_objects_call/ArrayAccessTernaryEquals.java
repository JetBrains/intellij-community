class T {
  static boolean same(String[] t, String[] s, int i) {
    return t[i] == null ? s[i] == null : t[i].<caret>equals(s[i]);
  }
}