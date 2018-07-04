class CompoundAssignmentSideEffect {

  void m() {
      // 2
      /*3*/
      /*4*/
      createSomeObject(/*1*/).b = false;
  }

  X createSomeObject() {
    return new X();
  }

  class X {
    boolean b = false;
  }
}