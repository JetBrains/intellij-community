// INTENTION_TEXT: "Convert to 'mapIndexed'"
// WITH_STDLIB
// AFTER-WARNING: Parameter 'index' is never used, could be renamed to _
fun test(list: List<String>) {
    list.<caret>map { s ->
        s + s
    }
}