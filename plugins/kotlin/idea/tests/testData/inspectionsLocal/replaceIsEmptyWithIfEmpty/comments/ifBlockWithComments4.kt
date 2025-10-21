// IGNORE_K1
fun test(l: List<Int>) {
    if (l.isNotEmpty<caret>()) // Replace with 'ifEmpty {...}
    // comment
    // comment
        l
    else listOf()
}