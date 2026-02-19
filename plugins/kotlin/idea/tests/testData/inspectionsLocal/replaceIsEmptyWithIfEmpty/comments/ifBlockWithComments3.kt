// IGNORE_K1
fun test(l: List<Int>) {
    <caret>if (l.isNotEmpty()) l
    // comment1
    // comment2
    else {
        // comment3
        // comment4
        listOf()
    }
}