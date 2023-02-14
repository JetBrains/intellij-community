// PROBLEM: none
// WITH_STDLIB
fun test(map: Map<String, Int>): Map.Entry<String, Int>? {
    return map.<caret>asSequence().firstOrNull()
}