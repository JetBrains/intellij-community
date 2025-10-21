// PROBLEM: none
// WITH_STDLIB

fun test(set: Set<Int>) {
    val toSet: Set<Int> = set.<caret>plus(emptySet()).toSet()
}