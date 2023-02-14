// IS_APPLICABLE: false
// WITH_STDLIB

fun foo(): List<String> = emptyList()

@Target(AnnotationTarget.TYPE)
annotation class Foo

fun test(): <caret>List<@Foo String> = foo()