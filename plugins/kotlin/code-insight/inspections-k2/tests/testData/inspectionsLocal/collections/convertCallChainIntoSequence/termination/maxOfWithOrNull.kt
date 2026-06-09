// WITH_STDLIB

fun test(list: List<Int>) {
    list.<caret>filter { it > 1 }.maxOfWithOrNull({ _, _ -> 0 }) { it }
}