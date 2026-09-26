// FIX: Merge call chain to 'associateTo'
// WITH_STDLIB
fun getKey(i: Int): Long = 1L
fun getValue(i: Int): String = ""

fun test(sequence: Sequence<Int>) {
    val target = mutableMapOf<Long, String>()
    sequence.<caret>map { getKey(it) to getValue(it) }.toMap(target)
}