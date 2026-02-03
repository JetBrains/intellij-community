// IGNORE_K1
fun test(l: List<Int>) {
    <caret>if (l.isNotEmpty()) {
        l
        // comment
        // comment
    } else listOf()
}