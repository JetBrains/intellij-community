// "Convert to 'mapNotNullTo'" "true"
// WITH_STDLIB
fun test(list: List<String?>) {
    list.mapIndexedNotNullTo(mutableListOf()) { <caret>index, s ->
        s
    }
}