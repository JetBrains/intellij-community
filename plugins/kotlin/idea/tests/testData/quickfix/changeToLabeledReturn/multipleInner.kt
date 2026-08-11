// "Change to 'return@forEach'" "true"
// ACTION: Change to 'return@foo'
// ACTION: Change to 'return@forEach'
// ACTION: Enable option 'Implicit receivers and parameters' for 'Lambdas' inlay hints
// ERROR: The integer literal does not conform to the expected type Unit
// WITH_STDLIB
// K2_AFTER_ERROR: RETURN_TYPE_MISMATCH
// K2_AFTER_ERROR: RETURN_TYPE_MISMATCH
// K2_ERROR: RETURN_NOT_ALLOWED
// K2_ERROR: RETURN_TYPE_MISMATCH

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
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeToLabeledReturnFix