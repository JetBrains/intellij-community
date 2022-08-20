// "Change type arguments to <*>" "false"
// ACTION: Compiler warning 'UNCHECKED_CAST' options
fun <T> test(list: List<*>) {
    val a: List<T> = list as List<T><caret>
}