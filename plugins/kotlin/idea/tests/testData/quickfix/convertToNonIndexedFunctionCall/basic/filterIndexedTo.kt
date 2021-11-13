// "Convert to 'filterTo'" "true"
// WITH_STDLIB
fun test(list: List<String>) {
    list.filterIndexedTo(mutableListOf()) { <caret>index, s ->
        s == "a"
    }
}