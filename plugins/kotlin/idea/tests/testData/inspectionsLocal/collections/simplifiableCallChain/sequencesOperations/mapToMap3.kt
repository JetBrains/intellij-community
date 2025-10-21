// FIX: Merge call chain to 'associateWith'
// WITH_STDLIB

// Issue: KTIJ-30620
// IGNORE_K2

fun getValue(i: Int): String = ""

fun test(sequence: Sequence<Int>) {
    val map: Map<Int, String> = sequence.<caret>map { it to getValue(it) }.toMap()
}