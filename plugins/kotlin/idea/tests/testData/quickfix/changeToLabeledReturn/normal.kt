// "Change to 'return@foo'" "true"
// K2_ERROR: RETURN_NOT_ALLOWED
// K2_ERROR: RETURN_TYPE_MISMATCH

fun foo(f:()->Int){}

fun bar() {
    foo {
        return<caret> 1
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeToLabeledReturnFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeToLabeledReturnFix