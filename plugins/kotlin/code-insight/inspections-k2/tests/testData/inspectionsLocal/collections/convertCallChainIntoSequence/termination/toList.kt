// WITH_STDLIB

fun test(set: Set<Int>) {
    val toList: List<Int> = set.<caret>plus(emptySet()).toList()
}