// INTENTION_TEXT: "Convert to 'onEachIndexed'"
// WITH_STDLIB
// AFTER-WARNING: Parameter 'index' is never used, could be renamed to _
fun test(list: List<String>) {
    list.<caret>onEach { s ->
        println(s)
    }
}