// FIX: Merge call chain to 'associateTo'
// WITH_STDLIB
fun getKey(i: Int): Long = 1L
fun getValue(i: Int): String = ""

fun test(list: List<Int>) {
    val target = mutableMapOf<Long, String>()
    list.<caret>map { getKey(it) to getValue(it) }.toMap(target)
}