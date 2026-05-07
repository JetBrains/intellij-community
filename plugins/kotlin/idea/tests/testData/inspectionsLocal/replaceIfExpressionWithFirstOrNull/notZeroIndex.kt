// PROBLEM: none
// WITH_STDLIB

fun test(children: List<String>): String? {
    return <caret>if (children.isNotEmpty()) {
        children[1]
    } else {
        null
    }
}
