fun <T> foo(): List<List<T>> = l

fun test() {
  val f: List<Int> = fo<caret>
}

// IGNORE_K2
// ELEMENT: foo