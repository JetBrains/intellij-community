// WITH_STDLIB
// AFTER-WARNING: Variable 'x' is never used
fun interface I<A, B, C, D> {
    fun method(x: List<Pair<A, List<B>>>): List<Pair<List<C>, D>>
}

fun main() {
    val x = <caret>I { x: List<Pair<Int, List<Long>>> -> emptyList<Pair<List<String>, Float>>() }
}
