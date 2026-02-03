// FIX: Merge call chain to 'associateBy'
// WITH_STDLIB

// Issue: KTIJ-30620
// IGNORE_K2

fun getKey(i: Int): Long = 1L

fun test(sequence: Sequence<Int>) {
    val map: Map<Long, Int> = sequence.<caret>map { getKey(it) to it }.toMap()
}