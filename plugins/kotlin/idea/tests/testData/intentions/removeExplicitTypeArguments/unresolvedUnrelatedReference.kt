// FIX: Remove explicit type arguments
// ERROR: Unresolved reference: unresolved1
// ERROR: Unresolved reference: unresolved2
// K2_ERROR: Unresolved reference 'unresolved1'.
// K2_ERROR: Unresolved reference 'unresolved2'.
// K2_AFTER_ERROR: Unresolved reference 'unresolved1'.
// K2_AFTER_ERROR: Unresolved reference 'unresolved2'.
fun foo() {
    unresolved1
    val x = "x"
    bar<caret><String>(x)
    unresolved2
}

fun <T> bar(t: T): T = t
