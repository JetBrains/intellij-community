class Braces {
  void m() {
    final boolean <caret>isEqualsVoorSyncResync = this instanceof A ? isEqual2() : isEqual();//keep me
  }

  private boolean isEqual() {
    return false;
  }

  private boolean isEqual2() {
    return false;
  }
}