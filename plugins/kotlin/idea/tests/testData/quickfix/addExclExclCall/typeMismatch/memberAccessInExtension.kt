// "Add non-null asserted (s!!) call" "true"
// K2_ERROR: Argument type mismatch: actual type is 'String?', but 'String' was expected.

class C {
    val s: String? = null
}

// Test for KTIJ-10052
fun C.test() {
    other(<caret>s)
}

fun other(s: String) {}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddExclExclCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddExclExclCallFix