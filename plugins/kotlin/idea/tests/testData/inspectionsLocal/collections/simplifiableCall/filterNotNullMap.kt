// WITH_STDLIB
// PROBLEM: none
fun test(map: Map<String?, String?>) {
    map.<caret>filter { it != null }
}