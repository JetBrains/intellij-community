// INTENTION_TEXT: "Replace with a 'forEachIndexed' function call"
// WITH_STDLIB
fun test() {
    <caret>for ((index, element) in listOf(1, 2, 3).withIndex()) {
        println("$index:$element")
    }
}