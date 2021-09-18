fun interface I<T> {
    fun method(x: T): T
}

typealias IInt = I<Int>

fun main() {
    val x = <caret>IInt { x -> x }
}
