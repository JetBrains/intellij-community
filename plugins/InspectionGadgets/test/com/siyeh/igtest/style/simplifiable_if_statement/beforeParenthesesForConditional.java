// "Replace 'if else' with '&&'" "true"
class IssueDemo {
  private String str1;
  private String str2;
  public boolean foo(IssueDemo c) {
    i<caret>f (str2 != null ? !str2.equals(c.str2) : c.str2 != null) return false;
    return str1 != null ? str1.equals(c.str1) : c.str1 == null;
  }
}
