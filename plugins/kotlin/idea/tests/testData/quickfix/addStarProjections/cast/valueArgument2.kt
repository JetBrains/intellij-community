// "Change type arguments to <*, *>" "false"
// ACTION: Add 'map =' to argument
// ACTION: Compiler warning 'UNCHECKED_CAST' options
fun test(a: Any) {
    foo(a as Map<Int, Boolean><caret>)
}

fun foo(map: Map<Int, Boolean>) {}