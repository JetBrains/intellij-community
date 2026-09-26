// WITH_STDLIB
fun test(set: Set<Int>): Set<Int> {
    return <caret>if (set.isEmpty()) {
        setOf(1)
    } else {
        set
    }
}