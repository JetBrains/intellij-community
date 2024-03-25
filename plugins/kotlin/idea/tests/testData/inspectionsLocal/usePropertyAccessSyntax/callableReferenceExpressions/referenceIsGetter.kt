// COMPILER_ARGUMENTS: -XXLanguage:+ReferencesToSyntheticJavaProperties
// PROBLEM: none
// LANGUAGE_VERSION: 2.1
fun main() {
    suppressUnused(Foo::<caret>isFoo)
}

fun suppressUnused(foo: (Foo) -> Boolean): Any = foo
