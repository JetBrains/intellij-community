// WITH_STDLIB
fun test(list: List<Int>): List<Int> {
    return <caret>if (list.isNotEmpty()) {
        list
    } else {
        listOf(1)
    }
}