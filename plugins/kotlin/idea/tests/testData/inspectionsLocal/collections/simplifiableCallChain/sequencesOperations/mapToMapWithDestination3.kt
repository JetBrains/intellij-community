// FIX: Merge call chain to 'associateWithTo'
// WITH_STDLIB

// Issue: KTIJ-30620
// IGNORE_K2

fun getValue(i: Int): String = ""

fun test(sequence: Sequence<Int>) {
    val target = mutableMapOf<Int, String>()
    sequence.<caret>map { it to getValue(it) }.toMap(target)
}