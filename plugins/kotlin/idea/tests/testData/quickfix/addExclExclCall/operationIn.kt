// "Add non-null asserted (!!) call" "true"
// WITH_STDLIB

fun foo(a: List<String>?) {
    "x" <caret>in a
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddExclExclCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddExclExclCallFix