// IS_APPLICABLE: false
// WITH_STDLIB
// PROBLEM: none
fun test(list: List<String>) {
    val x = list.<caret>count { it.startsWith("prefix_") } > 0
}