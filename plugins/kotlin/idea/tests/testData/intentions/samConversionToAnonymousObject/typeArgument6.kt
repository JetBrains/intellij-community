// AFTER-WARNING: Variable 'x' is never used
fun interface I<A, B, C> {
    fun method(x: A, y: B): C
}

typealias IIntInt<C> = I<Int, Int, C>

fun main() {
    val x = <caret>IIntInt { x, y -> x + y }
}
