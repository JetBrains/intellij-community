class MyList extends ArrayList {
  def foo() {
    ea<caret>chWithIndex { def x, def idx ->
      print x;
    }
  }
}
