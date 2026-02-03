// PROBLEM: none
// WITH_STDLIB
fun test(list: List<String>) {
    list.<caret>mapIndexed { index, value ->
        println(value)
        index + 42
    }
}