// "Change type arguments to <*>" "false"
// ACTION: Compiler warning 'UNCHECKED_CAST' options
// ACTION: Convert to block body
// ACTION: Introduce local variable
fun <T> test(list: List<*>): List<T> = list as List<T><caret>