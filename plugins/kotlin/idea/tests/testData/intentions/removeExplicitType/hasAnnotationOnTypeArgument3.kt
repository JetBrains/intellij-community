// IS_APPLICABLE: false
// WITH_STDLIB

@Target(AnnotationTarget.TYPE)
annotation class Foo

fun foo(): <caret>Map<String, Map<String, @Foo String>> = mapOf("" to mapOf("" to ""))