// IS_APPLICABLE: false
// WITH_STDLIB

fun foo(): List<List<String>> = emptyList()

@Target(AnnotationTarget.TYPE)
annotation class Foo

fun test(): <caret>List<List<@Foo String>> = foo()