// "Change to 'return@foo'" "true"

fun foo(f:()->Int){}

fun bar() {
    foo {
        return<caret> 1
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeToLabeledReturnFix