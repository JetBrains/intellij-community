// PROBLEM: none

class Test {
  operator fun Test.get(index: Int): Int {
    return index
  }
  operator fun String.provideDelegate(<caret>instance: Any?, property: Any): String = this
}
