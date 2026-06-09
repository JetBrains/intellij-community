// PROBLEM: none
// AFTER-WARNING: Parameter 'y' is never used
fun foo(y: Boolean) {
    val x = 4
    val z = 5
    <caret>x < z
}