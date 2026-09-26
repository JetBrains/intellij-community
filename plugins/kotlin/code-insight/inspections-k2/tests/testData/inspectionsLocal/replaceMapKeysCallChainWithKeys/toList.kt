// PROBLEM: none
// WITH_STDLIB

fun test(steps: Map<String, Int>): List<String> {
    return steps.<caret>map { it.key }.toList()
}
