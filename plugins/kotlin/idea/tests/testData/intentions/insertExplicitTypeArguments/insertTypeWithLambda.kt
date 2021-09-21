// IS_APPLICABLE: true
// AFTER-WARNING: Variable 'z' is never used
fun foo() {
      val z = <caret>bar { it * 2 }
}

fun <T> bar(a: (Int)->T): T = a(1)