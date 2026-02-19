// IS_APPLICABLE: false
// WITH_STDLIB
@Target(AnnotationTarget.TYPE)
annotation class Foo

fun <K, T> foo(x: (K) -> T): Pair<K, T> = TODO()

fun main() {
    val x = foo<String, Map<String, @Foo<caret> String>> { mapOf("" to "") }
}