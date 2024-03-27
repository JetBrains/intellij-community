// "Convert to 'scan'" "true"
// WITH_STDLIB
@OptIn(ExperimentalStdlibApi::class)
fun test(list: List<String>) {
    list.scanIndexed("") { <caret>index, acc, s ->
        acc + s
    }
}