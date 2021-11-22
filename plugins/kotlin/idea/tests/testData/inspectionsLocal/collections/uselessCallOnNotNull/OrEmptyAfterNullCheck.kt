// WITH_RUNTIME
fun test(str: String?) {
    if (str == null) return
    str.<caret>orEmpty()
}