package objects_require_non_null;

class Three {
  void a(Integer i) {
    assert ((i) != (null));
    System.out.println(i<caret>);
  }
}