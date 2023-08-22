// PROBLEM: none
// WITH_STDLIB
fun test() {
    arrayOf(listOf(1), listOf(2)).flatMap<caret> { it }
}