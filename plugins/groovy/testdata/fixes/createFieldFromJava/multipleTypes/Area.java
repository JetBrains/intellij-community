class M {
  public void foo() {
    Object x = new A().<caret>fld;
    fld = "";
  }
}