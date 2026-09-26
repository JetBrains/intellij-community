// PROBLEM: none
// WITH_STDLIB

fun test(steps: Map<String, Int>): Set<Int> {
    return steps.<caret>map { it.value }.toSet()
}
