// PROBLEM: none
class Foo : Runnable {
  override fun run() = this + this
}

private operator fun Foo.p<caret>lus(another: Foo) {}