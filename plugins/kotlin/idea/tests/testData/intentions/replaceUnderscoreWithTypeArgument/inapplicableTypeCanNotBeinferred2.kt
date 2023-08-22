// IS_APPLICABLE: false
// ERROR: Not enough information to infer type variable K
// WITH_STDLIB
fun <K, T> foo(x: (K) -> T): Pair<K, T> = TODO()

fun main() {
    val x = foo<<caret>_, Float> { it.toFloat() }
}