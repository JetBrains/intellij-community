// IS_APPLICABLE: false
// WITH_STDLIB
fun <K, T> foo(x: (K) -> T): Pair<K, T> = TODO()

fun main() {
    val x = foo<<caret>Int, Float> { it.toFloat() }
}