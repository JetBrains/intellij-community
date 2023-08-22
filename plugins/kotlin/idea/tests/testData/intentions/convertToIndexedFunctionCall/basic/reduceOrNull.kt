// INTENTION_TEXT: "Convert to 'reduceIndexedOrNull'"
// WITH_STDLIB
// TODO: fix warning?
// AFTER-WARNING: Parameter 'index' is never used, could be renamed to _
@OptIn(ExperimentalStdlibApi::class)
fun test(list: List<String>) {
    list.<caret>reduceOrNull { acc, s ->
        acc + s
    }
}