// "Add non-null asserted (arg!!) call" "true"
// DISABLE_ERRORS

fun foo(arg: String?) {
    if (arg == null) {
        arg<caret>.length
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddExclExclCallFix