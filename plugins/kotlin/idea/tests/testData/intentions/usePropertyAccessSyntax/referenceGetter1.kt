// COMPILER_ARGUMENTS: -XXLanguage:+ReferencesToSyntheticJavaProperties
fun main() {
    suppressUnused(Foo::<caret>getFoo)
}

fun suppressUnused(foo: (Foo) -> Int): Any = foo
