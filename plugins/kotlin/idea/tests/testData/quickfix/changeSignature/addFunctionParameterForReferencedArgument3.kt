// "Add parameter to function 'foo'" "true"
// DISABLE-ERRORS
fun foo() {}

fun test(isObject: Boolean) {
    foo((isObject)<caret>)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddFunctionParametersFix