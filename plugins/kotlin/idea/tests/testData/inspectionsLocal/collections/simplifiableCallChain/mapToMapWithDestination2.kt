// FIX: Merge call chain to 'associateByTo'
// WITH_STDLIB
fun getKey(i: Int): Long = 1L

fun test(list: List<Int>) {
    val target = mutableMapOf<Long, Int>()
    list.<caret>map { getKey(it) to it }.toMap(target)
}