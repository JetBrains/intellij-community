// FIX: Remove explicit type arguments
// AFTER-WARNING: Parameter 't' is never used
val x = "x"

fun foo() {
    bar<caret><String>(x)
}

fun <T> bar(t: T): Int = 1