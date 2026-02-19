// "Remove parameter 'x'" "true"
// DISABLE_ERRORS

fun f(<caret>x: Int, y: Int) {
    f(1, 2);
}

fun g(x: Int, y: Int) {
    f(x, y);
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveUnusedFunctionParameterFix