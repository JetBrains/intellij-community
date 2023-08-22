// WITH_STDLIB
fun test() {
    listOf(1, 2, 3).forEachIndexed<caret> { index, element ->
        println("$index: $element")
    }
}