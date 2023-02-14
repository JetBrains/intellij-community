// WITH_STDLIB
// AFTER-WARNING: Variable 'b' is never used
fun test(list: List<Int>) {
    val b = list.filter<caret> label1@ { return@label1 it == 1 }
}