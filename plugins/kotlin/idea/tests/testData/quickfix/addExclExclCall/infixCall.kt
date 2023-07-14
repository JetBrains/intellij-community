// "Add non-null asserted (!!) call" "true"

class SafeType {
    infix fun op(arg: Int) {}
}

fun safeB(p: SafeType?) {
    val v = p <caret>op 42
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddExclExclCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddExclExclCallFix