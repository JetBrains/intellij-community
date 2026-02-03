class BrokenCreateMethod {

  void foo(Map<String, String> bar) {
    Map<String, String[]> multiBar = new A().<caret>toMulti(bar)
  }
}