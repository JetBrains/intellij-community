class S {
  public void f() {
    <warning descr="int declaration">int i;</warning>
    int j;
    f();
    int k;
  }
}
