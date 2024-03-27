// "Convert to 'foldRight'" "true"
// WITH_STDLIB
fun test(list: List<String>) {
    list.foldRightIndexed("") { <caret>index, s, acc ->
        s + acc
    }
}