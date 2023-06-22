// FIR_IDENTICAL
// FIR_COMPARISON
fun <T1, T2> foo(): (T1) -> T2 = t

fun test() {
  val f: (Int) -> String = fo<caret>
}

// ELEMENT: foo