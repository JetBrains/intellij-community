// "Change type arguments to <*>" "true"
fun test(a: Any) = when (a) {
    is <caret>Array<String> -> 1
    else -> 2
}
