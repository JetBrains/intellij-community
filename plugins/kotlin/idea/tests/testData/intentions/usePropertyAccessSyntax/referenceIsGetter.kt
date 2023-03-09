// COMPILER_ARGUMENTS: -XXLanguage:+ReferencesToSyntheticJavaProperties
// IS_APPLICABLE: false
fun main() {
    suppressUnused(Foo::<caret>isFoo)
}

fun suppressUnused(foo: (Foo) -> Boolean): Any = foo
