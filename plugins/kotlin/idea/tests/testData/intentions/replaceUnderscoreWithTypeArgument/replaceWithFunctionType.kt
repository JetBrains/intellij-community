// AFTER-WARNING: Parameter 't' is never used

fun foo() {
    bar<<caret>_>({ i: Int -> 2 * i })
}

fun <T> bar(t: T): Int = 1