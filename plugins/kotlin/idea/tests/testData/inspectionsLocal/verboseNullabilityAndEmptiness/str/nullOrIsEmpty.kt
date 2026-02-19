// WITH_STDLIB
fun test(str: String?) {
    if (<caret>str == null || str.isEmpty()) println(0) else println(str.length)
}
