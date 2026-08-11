// "Add non-null asserted (i!!) call" "true"
// K2_ERROR: UNSAFE_CALL
fun test() {
    var i: Int? = 0
    i++<caret>
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddExclExclCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddExclExclCallFix