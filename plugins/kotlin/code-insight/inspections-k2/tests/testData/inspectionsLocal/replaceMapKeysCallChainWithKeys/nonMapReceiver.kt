// PROBLEM: none
// WITH_STDLIB

data class Step(val key: String)

fun test(steps: List<Step>): Set<String> {
    return steps.<caret>map { it.key }.toSet()
}
