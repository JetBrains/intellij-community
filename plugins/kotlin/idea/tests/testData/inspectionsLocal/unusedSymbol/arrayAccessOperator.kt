// PROBLEM: none

class Test {
  init {
    this[0]
  }
  private operator fun Test.<caret>get(index: Int): Int {
    return index
  }
}
