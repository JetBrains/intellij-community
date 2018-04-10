class InLoop {

  abstract void f(String s) throws Exception;

  void m() {
    for(int i = 0; i < 10; i++)try {
      f();
      System.out.println(i);
    } catch (Exception <caret>ignore) { }
  }
}