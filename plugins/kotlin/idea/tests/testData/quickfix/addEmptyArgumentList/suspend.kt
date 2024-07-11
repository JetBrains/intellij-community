// "Add empty argument list" "true"
fun suspend(fn: () -> Unit) {}

fun callSuspend() {
    suspend<caret> {  }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddEmptyArgumentListFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddEmptyArgumentListFix