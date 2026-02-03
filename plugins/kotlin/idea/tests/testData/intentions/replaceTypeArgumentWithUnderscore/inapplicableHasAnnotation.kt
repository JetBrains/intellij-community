// IS_APPLICABLE: false
// WITH_STDLIB
@Target(AnnotationTarget.TYPE)
annotation class Foo(val value: String)
fun <K, T> foo(x: (K) -> T): Pair<K, T> = TODO()

fun main() {
    val x = foo<Int, @Foo("bar") <caret> Float> { it.toFloat() }
}