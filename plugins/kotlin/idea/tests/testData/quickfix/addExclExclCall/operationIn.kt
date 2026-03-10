// "Add non-null asserted (a!!) call" "true"
// WITH_STDLIB
// K2_ERROR: Operator call is prohibited on a nullable receiver of type 'List<String>?'. Use '?.'-qualified call instead.

fun foo(a: List<String>?) {
    "x" <caret>in a
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddExclExclCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddExclExclCallFix