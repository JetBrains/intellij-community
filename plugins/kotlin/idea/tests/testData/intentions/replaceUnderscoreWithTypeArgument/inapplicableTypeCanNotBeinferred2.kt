// IS_APPLICABLE: false
// ERROR: Not enough information to infer type variable K
// WITH_STDLIB
// K2_ERROR: CANNOT_INFER_PARAMETER_TYPE
// K2_ERROR: CANNOT_INFER_PARAMETER_TYPE
fun <K, T> foo(x: (K) -> T): Pair<K, T> = TODO()

fun main() {
    val x = foo<<caret>_, Float> { it.toFloat() }
}