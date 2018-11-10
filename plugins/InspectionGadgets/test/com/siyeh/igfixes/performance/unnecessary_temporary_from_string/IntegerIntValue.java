class Test {
  void test(String s) {
    int x = new Integer(s+/*double-s*/s).intVal<caret>ue(/*hello*/);
  }
}
