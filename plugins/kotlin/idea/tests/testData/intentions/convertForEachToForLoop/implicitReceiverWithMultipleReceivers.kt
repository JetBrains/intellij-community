// WITH_STDLIB
fun List<Int>.doSmth2() {
    with (Any()) {
        <caret>forEach { element ->  }
    }
}