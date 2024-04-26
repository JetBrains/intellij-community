// LANGUAGE_VERSION: 2.1
// COMPILER_ARGUMENTS: -XXLanguage:+ReferencesToSyntheticJavaProperties
// FIX: Use property access syntax

fun main() {
    suppressUnused(Foo()::<caret>getFoo)
}

fun suppressUnused(foo: () -> Int): Any = foo