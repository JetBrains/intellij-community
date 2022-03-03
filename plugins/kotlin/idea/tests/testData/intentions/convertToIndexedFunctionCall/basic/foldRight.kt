// INTENTION_TEXT: "Convert to 'foldRightIndexed'"
// WITH_STDLIB
// AFTER-WARNING: Parameter 'index' is never used, could be renamed to _
fun test(list: List<String>) {
    list.<caret>foldRight("") { s, acc ->
        s + acc
    }
}