// AFTER-WARNING: The expression is unused
// AFTER-WARNING: The expression is unused
fun foo(b: Boolean) {
    <caret>when {
        // comment 1
        b -> 1 // comment 2

        else -> 2
    }
}

// IGNORE_FIR