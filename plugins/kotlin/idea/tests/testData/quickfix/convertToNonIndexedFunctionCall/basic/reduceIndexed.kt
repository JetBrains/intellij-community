// "Convert to 'reduce'" "true"
// WITH_STDLIB
fun test(list: List<String>) {
    list.reduceIndexed { <caret>index, acc, s ->
        acc + s
    }
}