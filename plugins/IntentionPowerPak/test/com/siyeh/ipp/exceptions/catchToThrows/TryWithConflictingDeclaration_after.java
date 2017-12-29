class TryWithConflictingDeclaration {

  abstract void f(String s) throws Exception;

  void m() throws Exception {
      {
          /* important comment */
          String s = "hello";
          f();
          // another comment
      }
    String s = "bye";
    System.out.println(s);
  }
}