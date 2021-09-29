// INTENTION_TEXT: "Convert to 'foldIndexed'"
// WITH_RUNTIME
// AFTER-WARNING: Parameter 'index' is never used, could be renamed to _
fun test(list: List<String>) {
    list.<caret>fold("") { acc, s ->
        acc + s
    }
}