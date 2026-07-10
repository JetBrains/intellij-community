// "Add non-null asserted (p!!) call" "true"
// K2_ERROR: UNSAFE_CALL

class SafeType {
    operator fun unaryMinus() {}
}

fun safeB(p: SafeType?) {
    val v = <caret>-p
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddExclExclCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddExclExclCallFix