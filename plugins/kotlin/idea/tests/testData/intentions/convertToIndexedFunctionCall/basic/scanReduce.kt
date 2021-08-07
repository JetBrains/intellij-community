// INTENTION_TEXT: "Convert to 'runningReduceIndexed'"
// WITH_RUNTIME
// DISABLE-ERRORS
@OptIn(ExperimentalStdlibApi::class)
fun test(list: List<String>) {
    list.<caret>runningReduce { acc, s ->
        acc + s
    }
}