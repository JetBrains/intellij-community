// WITH_STDLIB
// AFTER-WARNING: Variable 'x' is never used
// AFTER-WARNING: Variable 'z' is never used
fun main() {
    data class A(var x: Int)

    val <caret>a = A(0)
    val x = a.x

    run {
        val x = 1
        val z = a.x
    }
}