// COMPILER_ARGUMENTS: -XXLanguage:-ReferencesToSyntheticJavaProperties
// IS_APPLICABLE: false
fun main() {
    suppressUnused(Foo::<caret>getFoo)
}

fun suppressUnused(foo: (Foo) -> Int): Any = foo
