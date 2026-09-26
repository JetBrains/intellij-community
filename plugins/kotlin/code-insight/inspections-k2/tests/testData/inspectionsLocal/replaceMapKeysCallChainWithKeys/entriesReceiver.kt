// PROBLEM: none
// WITH_STDLIB

fun test(steps: Map<String, Int>): Set<String> {
    return steps.entries.<caret>map { it.key }.toSet()
}
