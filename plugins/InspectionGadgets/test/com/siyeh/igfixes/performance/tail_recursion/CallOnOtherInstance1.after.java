class CallOnOtherInstance1 {
  private boolean duplicate;
  private Something something;
  private CallOnOtherInstance1 original;

  public Something getSomething() {
      CallOnOtherInstance1 other = this;
      while (true) {
          if (other.something == null) {
              if (other.isDuplicate()) {
                  final CallOnOtherInstance1 recursion = other.getOriginal();
                  other = recursion;
                  continue;
              } else {
                  other.something = new Something();
              }
          }
          return other.something;
      }
  }

  private CallOnOtherInstance1 getOriginal() {
    return original;
  }
  private boolean isDuplicate() {
    return duplicate;
  }

  public static class Something {}
}