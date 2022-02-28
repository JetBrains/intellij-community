// INTENTION_TEXT: "Convert to 'scanIndexed'"
// WITH_RUNTIME
// TODO: fix warning?
// AFTER-WARNING: Parameter 'index' is never used, could be renamed to _
@OptIn(ExperimentalStdlibApi::class)
fun test(list: List<String>) {
    list.<caret>scan("") { acc, s ->
        acc + s
    }
}