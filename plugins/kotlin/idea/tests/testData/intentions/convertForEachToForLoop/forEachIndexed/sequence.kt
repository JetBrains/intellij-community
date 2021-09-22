// WITH_RUNTIME
fun test() {
    sequenceOf(1, 2, 3).forEachIndexed<caret> { index, element ->
        println("$index: $element")
    }
}