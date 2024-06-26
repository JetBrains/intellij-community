// "Convert to 'filter'" "true"
// WITH_STDLIB
fun test(list: List<String>) {
    list.filterIndexed { <caret>index, s ->
        s == "a"
    }
}