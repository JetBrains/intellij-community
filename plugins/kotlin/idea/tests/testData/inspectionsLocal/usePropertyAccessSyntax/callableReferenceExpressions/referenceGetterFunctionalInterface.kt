// COMPILER_ARGUMENTS: -XXLanguage:+ReferencesToSyntheticJavaProperties
// FIX: Use property access syntax
// LANGUAGE_VERSION: 2.1
fun main() {
    suppressUnused(Foo::<caret>getFoo)
}

fun suppressUnused(foo: Foo.FunInterface): Any = foo
