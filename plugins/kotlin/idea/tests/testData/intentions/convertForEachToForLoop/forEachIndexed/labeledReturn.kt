// WITH_STDLIB
fun test() {
    listOf(1, 2, 3).forEachIndexed<caret> { index, element ->
        if (index == 0) return@forEachIndexed
        println("$index: $element")
    }
}