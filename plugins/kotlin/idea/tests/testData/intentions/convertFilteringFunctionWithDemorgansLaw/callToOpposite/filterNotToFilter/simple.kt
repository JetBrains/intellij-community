// WITH_RUNTIME
// AFTER-WARNING: Variable 'b' is never used
fun test(list: List<Int>) {
    val b = list.filterNot<caret> { it != 1 }
}