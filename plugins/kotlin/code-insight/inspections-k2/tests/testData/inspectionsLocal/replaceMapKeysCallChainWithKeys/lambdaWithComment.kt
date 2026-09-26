// PROBLEM: none
// WITH_STDLIB

fun test(steps: Map<String, Int>): Set<String> {
    return steps.<caret>map {
        // Keep this comment.
        it.key
    }.toSet()
}
