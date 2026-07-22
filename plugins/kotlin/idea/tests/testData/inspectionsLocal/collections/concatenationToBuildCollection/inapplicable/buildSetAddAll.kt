// PROBLEM: none

fun testSetConcatenation(set1: Set<String>, set2: Set<String>, set3: Set<String>) {
    val set = buildSet {
        addAll(set1<caret>)
        addAll(set2)
        addAll(set3)
    }
}