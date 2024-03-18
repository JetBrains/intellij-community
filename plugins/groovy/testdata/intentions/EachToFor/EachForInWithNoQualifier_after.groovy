class MyList extends ArrayList {
  def foo() {
      for (<caret>x in this) {
          print x;
      }
  }
}
