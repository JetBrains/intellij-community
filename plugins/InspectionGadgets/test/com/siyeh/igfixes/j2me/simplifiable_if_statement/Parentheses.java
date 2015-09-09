class Parentheses {

  boolean m() {
    boolean a;
    boolean b;
    boolean c;
    if<caret> (a) {
      return false;
    } else {
      return b || c;
    }
  }
}