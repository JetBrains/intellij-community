// "Change to 'return@foo'" "true"
// ACTION: Change to 'return@foo'
// ACTION: Change to 'return@forEach'
// ACTION: Enable option 'Implicit receivers and parameters' for 'Lambdas' inlay hints
// WITH_STDLIB

fun foo(f:()->Int){}

fun bar() {

    foo {
        listOf(1).forEach {
            return<caret> 1
        }
        return@foo 1
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeToLabeledReturnFix