// "Convert to 'runningFold'" "true"
// WITH_STDLIB
fun test(list: List<String>) {
    list.runningFoldIndexed("") { <caret>index, acc, s ->
        acc + s
    }
}