// IS_APPLICABLE: false
// ERROR: Not enough information to infer type variable K
// WITH_STDLIB
// K2_ERROR: Cannot infer type for type parameter 'K'. Specify it explicitly.
// K2_ERROR: Cannot infer type for type parameter 'K'. Specify it explicitly.
fun <K, T> foo(x: (K) -> T): Pair<K, T> = TODO()

fun main() {
    val x = foo<<caret>_, Float> { it.toFloat() }
}