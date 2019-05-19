class CallOnOtherInstance2 {
  private boolean duplicate;
  private Something something;
  private CallOnOtherInstance2 original;

  public Something foo() {
      CallOnOtherInstance2 other = this;
      while (true) {
          if (!other.duplicate) {
              return other.something;
          } else {
              //comment
              other = other.getOriginal();
          }
      }
  }

  private CallOnOtherInstance2 getOriginal() {
    return original;
  }
  private boolean isDuplicate() {
    return duplicate;
  }

  public static class Something {}
}