// "Add non-null asserted (i!!) call" "true"
// K2_ERROR: UNSAFE_OPERATOR_CALL
fun test() {
    var i: Int? = 0
    i /=<caret> 2
}

// IGNORE_K2

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddExclExclCallFix