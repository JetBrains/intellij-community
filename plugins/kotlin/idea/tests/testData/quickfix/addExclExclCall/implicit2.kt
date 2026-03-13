// "Add non-null asserted (length!!) call" "true"
// WITH_STDLIB
// K2_ERROR: Only safe (?.) or non-null asserted (!!.) calls are allowed on a nullable receiver of type 'String?'.
fun test(s: String?) {
    s.run {
        <caret>length
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddExclExclCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddExclExclCallFix