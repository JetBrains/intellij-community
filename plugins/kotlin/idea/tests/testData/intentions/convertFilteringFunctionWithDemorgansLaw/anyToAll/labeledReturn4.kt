// IS_APPLICABLE: false
// WITH_STDLIB
// AFTER-WARNING: Variable 'b' is never used
fun test(list: List<Int>): Boolean {
    val b = list.any<caret> label1@ { return@test it != 1 }
    return false
}