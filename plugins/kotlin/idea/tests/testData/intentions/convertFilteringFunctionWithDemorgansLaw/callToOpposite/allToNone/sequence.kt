// WITH_STDLIB
// AFTER-WARNING: Variable 'b' is never used
fun test(seq: Sequence<Int>) {
    val b = seq.all<caret> { it != 1 }
}