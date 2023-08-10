// INTENTION_TEXT: "Convert to 'forEachIndexed'"
// WITH_STDLIB
// AFTER-WARNING: Parameter 'index1' is never used, could be renamed to _
fun test(list: List<String>) {
    list.forEach<caret> { index ->
        println(index)
    }
}