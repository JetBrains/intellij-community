// IS_APPLICABLE: false
// WITH_STDLIB

fun foo(): Map<String, Map<String, String>> = mapOf("" to mapOf("" to ""))

@Target(AnnotationTarget.TYPE)
annotation class Foo

fun test(): <caret>Map<String, Map<String, @Foo String>> = foo()