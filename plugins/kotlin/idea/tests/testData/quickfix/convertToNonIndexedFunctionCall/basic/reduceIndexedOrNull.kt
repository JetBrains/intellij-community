// "Convert to 'reduceOrNull'" "true"
// WITH_STDLIB
@OptIn(ExperimentalStdlibApi::class)
fun test(list: List<String>) {
    list.reduceIndexedOrNull { <caret>index, acc, s ->
        acc + s
    }
}