// FIX: Remove explicit type arguments
// ERROR: Unresolved reference: unresolved1
// ERROR: Unresolved reference: unresolved2
// K2_AFTER_ERROR: UNRESOLVED_REFERENCE
// K2_AFTER_ERROR: UNRESOLVED_REFERENCE
// K2_ERROR: UNRESOLVED_REFERENCE
// K2_ERROR: UNRESOLVED_REFERENCE
fun foo() {
    unresolved1
    val x = "x"
    bar<caret><String>(x)
    unresolved2
}

fun <T> bar(t: T): T = t
