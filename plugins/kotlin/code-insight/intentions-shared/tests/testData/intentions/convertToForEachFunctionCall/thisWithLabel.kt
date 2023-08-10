// WITH_STDLIB
fun List<Int>.test() {
    with (Any()) {
        <caret>for (element in this@test) {
            println(element)
        }
    }
}