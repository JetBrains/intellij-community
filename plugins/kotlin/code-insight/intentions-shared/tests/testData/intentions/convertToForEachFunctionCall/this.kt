// WITH_STDLIB
fun List<Int>.test() {
    <caret>for (element in this) {
        println(element)
    }
}