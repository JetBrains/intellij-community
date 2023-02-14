// "Change type arguments to <*>" "false"
// ACTION: Compiler warning 'UNCHECKED_CAST' options
fun <T> test(list: List<*>): List<T> {
    return list as List<T><caret>
}