// INTENTION_TEXT: "Convert to 'reduceRightIndexedOrNull'"
// WITH_STDLIB
// TODO: fix warning?
// AFTER-WARNING: Parameter 'index' is never used, could be renamed to _
@OptIn(ExperimentalStdlibApi::class)
fun test(list: List<String>) {
    list.<caret>reduceRightOrNull { s, acc ->
        s + acc
    }
}