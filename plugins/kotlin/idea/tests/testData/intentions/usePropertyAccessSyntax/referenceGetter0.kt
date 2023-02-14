// COMPILER_ARGUMENTS: -XXLanguage:+ReferencesToSyntheticJavaProperties
fun main() {
    suppressUnused(Foo()::<caret>getFoo)
}

fun suppressUnused(foo: () -> Int): Any = foo
