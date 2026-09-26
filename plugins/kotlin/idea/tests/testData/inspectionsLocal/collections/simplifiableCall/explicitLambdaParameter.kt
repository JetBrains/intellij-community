// WITH_STDLIB
fun test() {
    listOf(listOf(1)).<caret>flatMap { i -> i }
}