// "Add parameter to function 'bar'" "true"
// DISABLE-ERRORS
fun bar(isObject: Boolean) {}

fun test(isObject: Boolean) {
    bar(true, isObject<caret>)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddFunctionParametersFix