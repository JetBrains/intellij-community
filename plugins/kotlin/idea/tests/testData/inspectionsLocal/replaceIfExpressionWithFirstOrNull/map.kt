// PROBLEM: none
// WITH_STDLIB

fun test(values: Map<Int, String>): String? {
    return <caret>if (values.isNotEmpty()) {
        values[0]
    } else {
        null
    }
}
