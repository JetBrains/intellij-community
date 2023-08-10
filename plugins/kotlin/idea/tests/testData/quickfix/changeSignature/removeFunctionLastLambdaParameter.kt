// "Remove parameter 'block'" "true"
fun doNotUse1(<caret>block: () -> Unit) {}

fun useNotUse1() = doNotUse1 { }

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveUnusedFunctionParameterFix