// "Add non-null asserted (!!) call" "true"

class SafeType {
    operator fun unaryMinus() {}
}

fun safeB(p: SafeType?) {
    val v = <caret>-p
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddExclExclCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddExclExclCallFix