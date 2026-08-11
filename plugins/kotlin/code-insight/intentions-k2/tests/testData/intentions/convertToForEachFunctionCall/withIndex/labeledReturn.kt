// WITH_STDLIB
fun test() {
    <caret>for ((index, element) in listOf(1, 2, 3).withIndex()) {
        if (index == 0) continue
        println("$index:$element")
    }
}