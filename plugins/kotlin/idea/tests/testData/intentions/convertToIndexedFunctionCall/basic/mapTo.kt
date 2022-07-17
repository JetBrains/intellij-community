// INTENTION_TEXT: "Convert to 'mapIndexedTo'"
// WITH_STDLIB
// AFTER-WARNING: Parameter 'index' is never used, could be renamed to _
fun test(list: List<String>) {
    list.<caret>mapTo(mutableListOf()) { s ->
        s + s
    }
}