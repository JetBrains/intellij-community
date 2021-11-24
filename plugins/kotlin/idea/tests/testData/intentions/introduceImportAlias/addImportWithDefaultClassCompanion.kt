// WITH_STDLIB
// AFTER-WARNING: Variable 'max' is never used
// AFTER-WARNING: Variable 'max2' is never used
fun foo() {
    val max = Int.MAX_VALUE<caret>
    val max2 = Int.Companion.MAX_VALUE
}
