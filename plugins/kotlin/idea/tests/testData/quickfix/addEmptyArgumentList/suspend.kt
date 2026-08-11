// "Add empty argument list" "true"
// K2_ERROR: MODIFIER_FORM_FOR_NON_BUILT_IN_SUSPEND
fun suspend(fn: () -> Unit) {}

fun callSuspend() {
    suspend<caret> {  }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddEmptyArgumentListFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddEmptyArgumentListFix
// LANGUAGE_VERSION: 2.2