// WITH_STDLIB
fun test(array: Array<String>?) {
    if (<caret>array == null || array.isEmpty()) println(0) else println(array.size)
}
