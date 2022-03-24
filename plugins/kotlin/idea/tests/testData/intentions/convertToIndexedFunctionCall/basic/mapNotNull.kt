// INTENTION_TEXT: "Convert to 'mapIndexedNotNull'"
// WITH_STDLIB
// AFTER-WARNING: Parameter 'index' is never used, could be renamed to _
fun test(list: List<String?>) {
    list.<caret>mapNotNull { s ->
        s
    }
}