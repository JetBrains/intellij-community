class A {
  def f () {
    def l = new ArrayList()
    l.<caret>asImmutable()
  }
}
