class InLoopSingleLineConflictingDeclaration {

  abstract void f(String s) throws Exception;

  void m() throws Exception {
    for(int i = 0; i < 10; i++) {
        String fallacy;
    }
    String fallacy;
  }
}