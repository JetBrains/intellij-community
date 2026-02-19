// COMPILER_ARGUMENTS: -XXLanguage:+ReferencesToSyntheticJavaProperties
// PROBLEM: none
fun main() {
    suppressUnused(Foo::<caret>isFoo)
}

fun suppressUnused(foo: (Foo) -> Boolean): Any = foo
