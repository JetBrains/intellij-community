// WITH_STDLIB
fun test() {
    <caret>for ((index, element) in arrayOf(1).withIndex()) {
        println("$index:$element")
    }
}