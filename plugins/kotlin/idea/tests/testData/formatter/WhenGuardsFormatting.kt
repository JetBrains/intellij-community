// COMPILER_ARGUMENTS: -Xwhen-guards
// Issue: KT-72525

fun GuardNewlines(x: Any) {
    when (x) {
        // Test new-lines in guarded when conditions (with parentheses)
        is Boolean if
                                                  (x == true) -> Unit
        is Boolean if //e: Expecting an expression, Expecting '->'
                                                  (x == true)
        -> Unit

    }
}
