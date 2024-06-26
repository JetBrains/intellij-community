// "Convert to 'runningReduce'" "true"
// WITH_STDLIB
@OptIn(ExperimentalStdlibApi::class)
fun test(list: List<String>) {
    list.runningReduceIndexed { <caret>index, acc, s ->
        acc + s
    }
}