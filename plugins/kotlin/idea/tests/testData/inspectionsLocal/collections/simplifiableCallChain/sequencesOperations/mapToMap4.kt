// FIX: Merge call chain to 'associateWith'
// WITH_STDLIB

// Issue: KTIJ-30620
// IGNORE_K2

fun foo() {}

fun test(sequence: Sequence<Int>) {
    val map: Map<Int, Int> = sequence.<caret>map {
        foo()
        foo()
        it to it
    }.toMap()
}