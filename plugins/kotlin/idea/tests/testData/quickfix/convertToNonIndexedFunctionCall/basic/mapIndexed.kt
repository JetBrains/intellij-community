// "Convert to 'map'" "true"
// WITH_STDLIB
fun test(list: List<String>) {
    list.mapIndexed { <caret>index, s ->
        s + s
    }
}