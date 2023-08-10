// PROBLEM: none
// WITH_STDLIB
fun test() {
    listOf(listOf(1)).flatMap<caret> { it + 1 }
}