// WITH_STDLIB
fun test() {
    <caret>for ((index, element) in "123".withIndex()) {
        println("$index:$element")
    }
}