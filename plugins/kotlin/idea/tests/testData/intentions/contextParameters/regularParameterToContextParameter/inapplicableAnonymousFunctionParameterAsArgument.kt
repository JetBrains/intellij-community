// IS_APPLICABLE: false
// COMPILER_ARGUMENTS: -Xcontext-parameters

fun runIt(action: (Int) -> Unit) {
    action(1)
}

fun caller() {
    runIt(fun(<caret>n: Int) { n.inc() })
}
