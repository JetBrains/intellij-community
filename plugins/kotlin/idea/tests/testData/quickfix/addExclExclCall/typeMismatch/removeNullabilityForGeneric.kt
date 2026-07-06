// "Add non-null asserted (t!!) call" "true"
// K2_ERROR: ARGUMENT_TYPE_MISMATCH
interface Some

fun <T: Some?> test(t: T) {
    other(<caret>t)
}

fun other(s: Any) {}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddExclExclCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddExclExclCallFix