// FIX: Remove explicit type arguments
// AFTER-WARNING: Parameter 't' is never used
fun foo(x: String) {
    bar<caret><String>(x)
}

fun <T> bar(t: T): Int = 1