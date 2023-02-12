// SKIP_ERRORS_BEFORE
// AFTER-WARNING: The expression is unused
// AFTER-WARNING: The expression is unused
fun foo(i : Int) {
    <caret>when (0 == i) {
        true -> 1
        true -> 2
    }
}

// IGNORE_FIR
