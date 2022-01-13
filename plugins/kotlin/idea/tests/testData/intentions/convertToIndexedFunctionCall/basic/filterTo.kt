// INTENTION_TEXT: "Convert to 'filterIndexedTo'"
// WITH_RUNTIME
// AFTER-WARNING: Parameter 'index' is never used, could be renamed to _
fun test(list: List<String>) {
    list.<caret>filterTo(mutableListOf()) { s ->
        s == "a"
    }
}