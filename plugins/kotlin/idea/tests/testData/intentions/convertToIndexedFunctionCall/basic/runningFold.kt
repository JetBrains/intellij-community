// INTENTION_TEXT: "Convert to 'runningFoldIndexed'"
// WITH_RUNTIME
// AFTER-WARNING: Parameter 'index' is never used, could be renamed to _
fun test(list: List<String>) {
    list.<caret>runningFold("") { acc, s ->
        acc + s
    }
}