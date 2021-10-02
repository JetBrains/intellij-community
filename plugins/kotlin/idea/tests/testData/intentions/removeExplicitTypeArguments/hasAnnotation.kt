// IS_APPLICABLE: false
// WITH_RUNTIME
@Target(AnnotationTarget.TYPE)
annotation class Foo(val value: String)

fun main() {
    val l = listOf<@Foo("bar") <caret>String>("")
}