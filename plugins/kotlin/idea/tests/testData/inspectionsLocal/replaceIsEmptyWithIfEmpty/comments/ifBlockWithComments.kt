// IGNORE_K1
fun test(l: List<Int>) {
    if (l.isNotEmpty<caret>()) l // Replace with 'ifEmpty {...}
    // comment
    // comment
    else listOf()
}