// COMPILER_ARGUMENTS: -XXLanguage:+ReferencesToSyntheticJavaProperties
// FIX: Use property access syntax
fun main() {
    suppressUnused(Foo::<caret>getFoo)
}

fun suppressUnused(foo: Foo.FunInterface): Any = foo
