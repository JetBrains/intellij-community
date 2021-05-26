class ThrowNull {
  public void x() throws <caret>Exception {
    if (true) {
      throw new IllegalArgumentException();
    }
    throw null;
  }
}