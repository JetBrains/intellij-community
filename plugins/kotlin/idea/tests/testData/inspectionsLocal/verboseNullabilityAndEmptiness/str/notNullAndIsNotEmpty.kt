// WITH_STDLIB
fun test(str: String?) {
    if (<caret>str != null && str.isNotEmpty()) println(str.length) else println(0)
}
