// FIX: Remove explicit type arguments
// AFTER-WARNING: Parameter 'k' is never used
// AFTER-WARNING: Parameter 'r' is never used
// AFTER-WARNING: Parameter 't' is never used
// AFTER-WARNING: Parameter 'v' is never used
// AFTER-WARNING: Variable 'z' is never used
fun foo() {
    val x = "1"
    val y = 2
    val z = bar<caret><String, Int, Int, String>(x, 1, y, "x")
}

fun <T, V, R, K> bar(t: T, v: V, r: R, k: K): Int = 2