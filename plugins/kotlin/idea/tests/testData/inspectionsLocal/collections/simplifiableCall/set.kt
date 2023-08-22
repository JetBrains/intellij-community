// WITH_STDLIB
fun test() {
    setOf(setOf(1)).<caret>flatMap { it }
}