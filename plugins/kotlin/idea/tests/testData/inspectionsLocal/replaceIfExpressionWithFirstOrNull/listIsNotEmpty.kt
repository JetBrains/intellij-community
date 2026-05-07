// FIX: Replace with 'firstOrNull()'
// WITH_STDLIB

fun test(children: List<String>): String? {
    return <caret>if (children.isNotEmpty()) {
        children.get(0)
    } else {
        null
    }
}
