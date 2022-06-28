// "Change type arguments to <*, *>" "false"
// ACTION: Compiler warning 'UNCHECKED_CAST' options
// ACTION: Do not show return expression hints
fun test(a: Any) {
    (a as Map<Int, Boolean><caret>).bar()
}

fun Map<Int, Boolean>.bar() {}