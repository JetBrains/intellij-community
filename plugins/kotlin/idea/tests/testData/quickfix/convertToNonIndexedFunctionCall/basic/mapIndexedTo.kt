// "Convert to 'mapTo'" "true"
// WITH_STDLIB
fun test(list: List<String>) {
    list.mapIndexedTo(mutableListOf()) { <caret>index, s ->
        s + s
    }
}