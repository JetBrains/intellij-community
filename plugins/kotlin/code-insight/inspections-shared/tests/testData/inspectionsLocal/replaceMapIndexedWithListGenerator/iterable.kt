// PROBLEM: none
// WITH_STDLIB
fun test(list: Iterable<String>) {
    list.<caret>mapIndexed { index, _ ->
        index + 42
    }
}