// "Remove parameter 'x'" "true"

fun Int.f(<caret>x: Int) {

}

fun test() {
    1.f(2)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveUnusedFunctionParameterFix