// "Change type arguments to <*>" "false"
// ACTION: Compiler warning 'UNCHECKED_CAST' options
fun test(a: Any) {
    (a as List<Boolean><caret>).bar()
}

fun List<Boolean>.bar() {}