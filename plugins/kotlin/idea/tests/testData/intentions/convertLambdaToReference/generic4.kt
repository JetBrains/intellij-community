// WITH_STDLIB
fun test() {
    listOf(listOf(1)).filter <caret>{ it.isNotEmpty() }
}