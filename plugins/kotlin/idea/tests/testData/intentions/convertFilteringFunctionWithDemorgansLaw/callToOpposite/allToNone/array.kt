// WITH_STDLIB
// AFTER-WARNING: Variable 'b' is never used
fun test(array: IntArray) {
    val b = array.all<caret> { it != 1 }
}