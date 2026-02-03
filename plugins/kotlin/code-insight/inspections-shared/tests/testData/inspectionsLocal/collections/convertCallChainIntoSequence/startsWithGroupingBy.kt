// PROBLEM: none
// WITH_STDLIB

fun test(list: List<Int>) {
    list.<caret>groupingBy { it }.reduce { _, acc, _ -> acc }
}