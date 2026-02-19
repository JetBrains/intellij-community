// WITH_STDLIB
fun List<Int>.test() {
    <caret>for ((index, element) in withIndex()) {
        println("$index:$element")
    }
}