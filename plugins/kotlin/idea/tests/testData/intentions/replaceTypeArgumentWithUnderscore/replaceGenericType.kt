// AFTER-WARNING: Parameter 't' is never used

fun <T> foo(t: T) {
    bar<<caret>T>(t)
}

fun <T> bar(t: T): Int = 1