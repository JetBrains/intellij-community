class Foo {
  def foo2() {
    label:
    Exception e
    <ref>e.getLocalizedMessage() // code completion and navigation from usage of "e" to declaration of "e" don't work
  }
}