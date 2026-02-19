// WITH_STDLIB
fun test(set: Set<Int>): Set<Int> {
    return <caret>if (set.isNotEmpty()) {
        set
    } else {
        setOf(1)
    }
}