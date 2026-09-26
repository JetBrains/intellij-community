// FIX: Replace with 'keys'
// WITH_STDLIB

fun test(steps: Map<String, Int>): Set<String> {
    return steps.<caret>map { step -> step.key }.toSet()
}
