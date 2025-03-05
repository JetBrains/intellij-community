// FIX: Remove explicit type arguments
// AFTER-WARNING: Parameter 't' is never used
fun foo() {
    bar<caret><(Int) -> Int> { it: Int -> it }
}

fun <T> bar(t: T): Int = 1