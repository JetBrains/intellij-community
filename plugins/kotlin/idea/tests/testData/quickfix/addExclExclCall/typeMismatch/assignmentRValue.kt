// "Add non-null asserted (s!!) call" "true"
// K2_ERROR: Assignment type mismatch: actual type is 'String?', but 'String' was expected.
fun test(s: String?) {
    var z: String = ""
    z = <caret>s
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddExclExclCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddExclExclCallFix