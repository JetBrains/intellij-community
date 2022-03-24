// WITH_STDLIB
fun test(str: String?) {
    if (<caret>str != null && str.isNotBlank()) println(str.length) else println(0)
}
