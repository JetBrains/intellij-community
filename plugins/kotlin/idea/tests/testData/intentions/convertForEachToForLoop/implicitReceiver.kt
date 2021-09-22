// WITH_RUNTIME
fun List<Int>.test() {
    <caret>forEach { element ->
        println(element)
    }
}