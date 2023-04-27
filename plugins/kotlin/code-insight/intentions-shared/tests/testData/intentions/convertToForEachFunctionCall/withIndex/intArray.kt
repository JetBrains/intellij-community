// WITH_STDLIB
fun test() {
    <caret>for ((index, element) in intArrayOf(1).withIndex()) {
        println("$index:$element")
    }
}