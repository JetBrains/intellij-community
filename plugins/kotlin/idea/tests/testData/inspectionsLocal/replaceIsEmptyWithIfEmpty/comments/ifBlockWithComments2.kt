// IGNORE_K1
fun test(l: List<Int>) {
    if (l.isNotEmpty<caret>()) {
        l
        // comment
        // comment
    } else listOf()
}