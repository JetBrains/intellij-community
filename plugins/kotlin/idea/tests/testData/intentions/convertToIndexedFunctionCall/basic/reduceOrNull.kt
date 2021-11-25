// INTENTION_TEXT: "Convert to 'reduceIndexedOrNull'"
// WITH_RUNTIME
// AFTER-WARNING: This class can only be used with the compiler argument '-opt-in=kotlin.RequiresOptIn'
// TODO: fix warning?
// AFTER-WARNING: Parameter 'index' is never used, could be renamed to _
@OptIn(ExperimentalStdlibApi::class)
fun test(list: List<String>) {
    list.<caret>reduceOrNull { acc, s ->
        acc + s
    }
}