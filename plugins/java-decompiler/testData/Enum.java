public enum Enum {
  E1,
  E2() {
    @Override
    public void m() { }
  },
  E3("-"),
  E4("+") {
    @Override
    public void m() { }
  };

  public void m() { }

  private String s;

  private Enum() { this("?"); }
  private Enum(@Deprecated String s) { this.s = s; }
}
