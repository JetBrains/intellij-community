// IS_APPLICABLE: false
// WITH_STDLIB
fun test(list: List<String>) {
    val x = list.<caret>count { it.startsWith("prefix_") } > 0
}