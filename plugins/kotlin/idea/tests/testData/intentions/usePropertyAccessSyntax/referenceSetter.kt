// COMPILER_ARGUMENTS: -XXLanguage:+ReferencesToSyntheticJavaProperties
// IS_APPLICABLE: false
fun main() {
    suppressUnused(Foo::<caret>setFoo)
}

fun suppressUnused(foo: (Foo, Int) -> Unit): Any = foo
