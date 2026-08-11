// "Add non-null asserted (s!!) call" "true"
// K2_ERROR: ASSIGNMENT_TYPE_MISMATCH
fun test(s: String?) {
    var z: String = ""
    z = <caret>s
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddExclExclCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddExclExclCallFix