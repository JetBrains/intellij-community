// WITH_STDLIB
fun test(list: List<Int>?) {
    list?.forEachIndexed<caret> { index, element -> println("$index: $element") }
}