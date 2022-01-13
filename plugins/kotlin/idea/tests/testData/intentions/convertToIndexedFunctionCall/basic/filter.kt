// INTENTION_TEXT: "Convert to 'filterIndexed'"
// WITH_RUNTIME
// AFTER-WARNING: Parameter 'index' is never used, could be renamed to _
fun test(list: List<String>) {
    list.<caret>filter { s ->
        s == "a"
    }
}