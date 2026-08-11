// IS_APPLICABLE: false
// ERROR: Unresolved reference: s
// K2_ERROR: CANNOT_INFER_PARAMETER_TYPE
// K2_ERROR: UNRESOLVED_REFERENCE
fun foo() {
    val x = <caret>bar(s) // s not definded, can't be inferred
}

fun <T> bar(t: T): Int = 1