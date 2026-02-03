// INTENTION_TEXT: "Convert to 'runningFoldIndexed'"
// WITH_STDLIB
// AFTER-WARNING: Parameter 'index' is never used, could be renamed to _
fun test(list: List<String>) {
    list.<caret>runningFold("") { acc, s ->
        acc + s
    }
}