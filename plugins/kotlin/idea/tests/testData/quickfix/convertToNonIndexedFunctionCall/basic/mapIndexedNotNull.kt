// "Convert to 'mapNotNull'" "true"
// WITH_STDLIB
fun test(list: List<String?>) {
    list.mapIndexedNotNull { <caret>index, s ->
        s
    }
}