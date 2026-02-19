// "Add non-null asserted (a!!) call" "true"
// WITH_STDLIB

fun foo(a: String?) {
    a<caret>.lowercase()
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddExclExclCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddExclExclCallFix