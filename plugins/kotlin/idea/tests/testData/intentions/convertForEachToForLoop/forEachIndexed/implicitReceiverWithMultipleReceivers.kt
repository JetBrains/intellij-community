// WITH_STDLIB
fun List<Int>.test() {
    with(Any()) {
        forEachIndexed<caret> { index, element ->
            println("$index: $element")
        }
    }
}