// IGNORE_K1
fun test(l: List<Int>) {
    if (l.isNotEmpty<caret>()) l
    // comment1
    // comment2
    else {
        // comment3
        // comment4
        listOf()
    }
}