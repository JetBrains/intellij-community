// "Add empty argument list" "true"
// K2_ERROR: Calls in the form of 'suspend {}' are deprecated because 'suspend' in this context will have the meaning of a modifier. Surround the lambda with parentheses: 'suspend({ ... })'.
fun suspend(fn: () -> Unit) {}

fun callSuspend() {
    suspend<caret> {  }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddEmptyArgumentListFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddEmptyArgumentListFix
// LANGUAGE_VERSION: 2.2