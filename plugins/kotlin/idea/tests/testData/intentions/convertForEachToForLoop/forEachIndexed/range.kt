// WITH_RUNTIME
fun test() {
    (1..3).forEachIndexed<caret> { index, element ->
        println("$index: $element")
    }
}