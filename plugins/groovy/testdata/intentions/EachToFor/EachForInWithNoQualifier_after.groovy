class MyList extends ArrayList {
  def foo() {
      fo<caret>r (x in this) {
          print x;
      }
  }
}
