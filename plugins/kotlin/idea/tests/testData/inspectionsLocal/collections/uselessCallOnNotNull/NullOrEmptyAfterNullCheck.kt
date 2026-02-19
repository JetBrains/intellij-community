// WITH_STDLIB
fun test(str: String?) {
    if (str == null) return
    str.<caret>isNullOrEmpty()
}