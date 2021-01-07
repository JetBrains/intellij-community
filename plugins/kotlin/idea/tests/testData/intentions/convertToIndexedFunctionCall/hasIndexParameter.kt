// INTENTION_TEXT: "Convert to 'forEachIndexed'"
// WITH_RUNTIME
fun test(list: List<String>) {
    list.forEach<caret> { index ->
        println(index)
    }
}