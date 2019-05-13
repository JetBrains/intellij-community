class NoCondition {
  void m(Object o) {
    <caret>if () {
      throw new NullPointerException("wtf?");
    }
  }
}