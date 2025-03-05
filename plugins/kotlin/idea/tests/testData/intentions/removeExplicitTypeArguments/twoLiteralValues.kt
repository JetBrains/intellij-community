// FIX: Remove explicit type arguments
// AFTER-WARNING: Parameter 't' is never used
// AFTER-WARNING: Parameter 'v' is never used
// AFTER-WARNING: Variable 'x' is never used
fun foo() {
    val x = bar<caret><String, Int>("x", 0)
}

fun <T, V> bar(t: T, v: V): Int = 1