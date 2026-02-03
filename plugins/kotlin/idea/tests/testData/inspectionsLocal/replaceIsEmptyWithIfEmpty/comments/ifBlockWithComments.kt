// IGNORE_K1
fun test(l: List<Int>) {
    <caret>if (l.isNotEmpty()) l // Replace with 'ifEmpty {...}
    // comment
    // comment
    else listOf()
}