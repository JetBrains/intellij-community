// IS_APPLICABLE: false
// WITH_STDLIB

@Target(AnnotationTarget.TYPE)
annotation class Foo

fun foo(): <caret>List<@Foo String> = listOf("")