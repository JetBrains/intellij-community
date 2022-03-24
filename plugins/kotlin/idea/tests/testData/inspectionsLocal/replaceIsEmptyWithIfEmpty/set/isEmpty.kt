// WITH_STDLIB
fun test(set: Set<Int>): Set<Int> {
    return if (set.isEmpty<caret>()) {
        setOf(1)
    } else {
        set
    }
}