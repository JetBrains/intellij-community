// "Replace 'if else' with '&&'" "true"
class MoreParentheses {
  public boolean simp(boolean A, boolean B, boolean C) {
    if<caret> (A || B) return C;
    else return false;
  }
}