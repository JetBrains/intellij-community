// INTENTION_TEXT: "Convert to 'scanIndexed'"
// WITH_STDLIB
// AFTER-WARNING: This annotation should be used with the compiler argument '-opt-in=kotlin.RequiresOptIn'
// TODO: fix warning?
// AFTER-WARNING: Parameter 'index' is never used, could be renamed to _
@OptIn(ExperimentalStdlibApi::class)
fun test(list: List<String>) {
    list.<caret>scan("") { acc, s ->
        acc + s
    }
}