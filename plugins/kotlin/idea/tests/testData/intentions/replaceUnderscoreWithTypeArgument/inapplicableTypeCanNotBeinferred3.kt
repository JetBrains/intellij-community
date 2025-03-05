// IS_APPLICABLE: false
// ERROR: Unresolved reference: s
// K2_ERROR: Cannot infer type for this parameter. Specify it explicitly.
// K2_ERROR: Unresolved reference 's'.
fun foo() {
    val x = bar<<caret>_>(s) // s not definded, can't be inferred
}

fun <T> bar(t: T): Int = 1