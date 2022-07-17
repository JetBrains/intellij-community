// IS_APPLICABLE: false
// WITH_STDLIB
@Target(AnnotationTarget.TYPE)
annotation class Foo(val value: String)

fun main() {
    val l = listOf<@Foo("bar") <caret>String>("")
}