// WITH_RUNTIME
fun test() {
    "123".forEachIndexed<caret> { index, element ->
        println("$index: $element")
    }
}