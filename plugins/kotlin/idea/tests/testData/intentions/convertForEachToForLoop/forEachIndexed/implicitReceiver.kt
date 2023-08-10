// WITH_STDLIB
fun List<Int>.test() {
    forEachIndexed<caret> { index, element ->
        println("$index: $element")
    }
}