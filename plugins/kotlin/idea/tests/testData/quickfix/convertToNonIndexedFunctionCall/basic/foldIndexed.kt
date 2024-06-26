// "Convert to 'fold'" "true"
// WITH_STDLIB
fun test(list: List<String>) {
    list.foldIndexed("") { <caret>index, acc, s ->
        acc + s
    }
}