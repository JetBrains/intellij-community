// WITH_STDLIB
// AFTER-WARNING: Variable 'b' is never used
fun test(list: List<Int>) {
    val b = !list.<caret>none label1@ { return@label1 it == 1 }
}