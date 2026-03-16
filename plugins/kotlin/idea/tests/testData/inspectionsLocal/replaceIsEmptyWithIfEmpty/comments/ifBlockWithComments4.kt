// IGNORE_K1
fun test(l: List<Int>) {
    <caret>if (l.isNotEmpty()) // Replace with 'ifEmpty {...}
    // comment
    // comment
        l
    else listOf()
}