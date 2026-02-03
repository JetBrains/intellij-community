// INTENTION_TEXT: "Convert to 'forEachIndexed'"
// WITH_STDLIB
// AFTER-WARNING: Parameter 'index' is never used
// AFTER-WARNING: Parameter 'index1' is never used, could be renamed to _
fun test(list: List<String>, index: Int) {
    list.<caret>forEach { s ->
        println(s)
    }
}