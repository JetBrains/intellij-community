// FIX: Merge call chain to 'associateWithTo'
// WITH_STDLIB

// Issue: KTIJ-30620
// IGNORE_K2

fun getValue(i: Int): String = ""

fun test(list: List<Int>) {
    val target = mutableMapOf<Int, String>()
    list.<caret>map { it to getValue(it) }.toMap(target)
}