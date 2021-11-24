// WITH_STDLIB
fun List<Int>.test() {
    <caret>forEach { element ->
        println(element)
    }
}