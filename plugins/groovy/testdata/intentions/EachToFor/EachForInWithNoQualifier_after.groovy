class MyList extends ArrayList {
  def foo() {
      for (x<caret> in this) {
          print x;
      }
  }
}
