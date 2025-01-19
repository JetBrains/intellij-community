// IS_APPLICABLE: false
// ERROR: Unresolved reference: s
// K2-ERROR: Cannot infer type for this parameter. Specify it explicitly.
// K2-ERROR: Unresolved reference 's'.
fun foo() {
    val x = <caret>bar(s) // s not definded, can't be inferred
}

fun <T> bar(t: T): Int = 1