// FIX: Replace with 'firstOrNull()'
// WITH_STDLIB

fun test(children: List<String>): String? {
    return <caret>if (children.size > 0) {
        children[0]
    } else {
        null
    }
}
