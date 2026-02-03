// WITH_STDLIB
fun test() {
    <caret>for ((index, element) in (1..3).withIndex()) {
        println("$index:$element")
    }
}