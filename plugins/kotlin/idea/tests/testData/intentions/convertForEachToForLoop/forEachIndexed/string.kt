// WITH_STDLIB
fun test() {
    "123".forEachIndexed<caret> { index, element ->
        println("$index: $element")
    }
}