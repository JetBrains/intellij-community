// IS_APPLICABLE: true
// AFTER-WARNING: Parameter 'k' is never used
// AFTER-WARNING: Parameter 't' is never used
// AFTER-WARNING: Parameter 'v' is never used
fun foo() {
    bar<String, Int, Int>("x", 1, 2) <caret>{ it }
}

fun <T, V, K> bar(t: T, v: V, k: K, a: (Int)->Int): Int {
    return a(1)
}