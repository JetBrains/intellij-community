// PROBLEM: none
// WITH_STDLIB
fun test() {
    listOf("A").forEach {
        setOf(1).map { <caret>_ -> it.length }
    }
}