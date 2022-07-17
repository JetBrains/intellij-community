// IS_APPLICABLE: false
// WITH_STDLIB
// AFTER-WARNING: Variable 'b' is never used
fun test(list: List<Int>): Boolean {
    val b = list.any<caret> { return@test it != 1 }
    return false
}