class MyList extends ArrayList {
  def foo() {
      for (idx, <caret>x in this) {
          print x;
      }
  }
}
