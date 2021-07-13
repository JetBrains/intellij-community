// IS_APPLICABLE: false
fun interface I<A> {
    fun method(x: A): Int
}

typealias IInA<A> = I<in A>

fun main() {
    val x = <caret>IInA<Int> { x -> x }
}