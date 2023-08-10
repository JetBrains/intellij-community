// "Change type arguments to <*, *>" "false"
// ACTION: Compiler warning 'UNCHECKED_CAST' options
fun test(a: Any) {
    (a as Map<Int, Boolean><caret>).bar()
}

fun Map<Int, Boolean>.bar() {}