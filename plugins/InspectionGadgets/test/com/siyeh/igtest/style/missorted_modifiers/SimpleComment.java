class SimpleComment {

  void foo(<warning descr="Missorted modifiers 'final/* comment */ @Deprecated'">final/* comment */ @Depr<caret>ecated</warning> String project) {

  }
}