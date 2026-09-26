// FIX: Merge call chain to 'associateByTo'
// WITH_STDLIB

// Issue: KTIJ-30620
// IGNORE_K2

fun getKey(i: Int): Long = 1L

fun test(list: List<Int>) {
    val target = mutableMapOf<Long, Int>()
    list.<caret>map { getKey(it) to it }.toMap(target)
}