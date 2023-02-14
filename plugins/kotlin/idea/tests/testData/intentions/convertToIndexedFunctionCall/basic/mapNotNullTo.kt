// INTENTION_TEXT: "Convert to 'mapIndexedNotNullTo'"
// WITH_STDLIB
// AFTER-WARNING: Parameter 'index' is never used, could be renamed to _
fun test(list: List<String?>) {
    list.<caret>mapNotNullTo(mutableListOf()) { s ->
        s
    }
}