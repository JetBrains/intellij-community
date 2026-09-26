// FIX: Replace with 'keys'
// WITH_STDLIB

fun test(steps: Map<String, Int>): Set<String> = with(steps) {
    m<caret>ap { it.key }.toSet()
}
