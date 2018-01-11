class InLoopSingleLineConflictingDeclaration {

  abstract void f(String s) throws Exception;

  void m() {
    for(int i = 0; i < 10; i++)try {
      String fallacy;
    } catch (Exception <caret>ignore) { }
    String fallacy;
  }
}