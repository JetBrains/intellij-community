// "Add non-null asserted (a!!) call" "true"
// K2_ERROR: UNSAFE_CALL
fun foo(a: Int?) {
    a.<caret>plus(1)
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddExclExclCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddExclExclCallFix