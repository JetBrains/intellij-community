// "Remove parameter 'a'" "true"

fun String.foo(<caret>a:Int = 0, b: Int) {
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveUnusedFunctionParameterFix