// "Convert to 'reduceRightOrNull'" "true"
// WITH_STDLIB
@OptIn(ExperimentalStdlibApi::class)
fun test(list: List<String>) {
    list.reduceRightIndexedOrNull { <caret>index, s, acc ->
        s + acc
    }
}