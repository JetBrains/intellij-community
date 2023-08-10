// WITH_STDLIB
fun test(list: List<String>) {
    list.<caret>mapIndexed { index, _ ->
        index + 42
    }
}