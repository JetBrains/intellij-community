public class B {
  public static class Inner {
    public boolean equals(Object o) {
      return o instanceof Inner;
    }
  }
  Inner i = new Inner();
}