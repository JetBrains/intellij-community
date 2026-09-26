// IS_APPLICABLE: false
// COMPILER_ARGUMENTS: -Xcontext-parameters

fun runIt(action: (Int) -> Unit) {
    action(1)
}

fun caller() {
    runIt { <caret>n -> n.inc() }
}
