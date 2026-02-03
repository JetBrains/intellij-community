// PROBLEM: none
// IS_APPLICABLE: false
// WITH_STDLIB

@Target(AnnotationTarget.TYPE)
annotation class Foo

fun main() {
    val l = mapOf<String, <caret>Map<String, @Foo String>>("" to mapOf("" to ""))
}