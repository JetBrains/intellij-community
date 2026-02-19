// AFTER-WARNING: Variable 'first' is never used
// AFTER-WARNING: Variable 'second' is never used
data class Data(val first: Int, val second: Int)

fun foo() {
    val (first, <caret>_) = Data(1, 2)
}