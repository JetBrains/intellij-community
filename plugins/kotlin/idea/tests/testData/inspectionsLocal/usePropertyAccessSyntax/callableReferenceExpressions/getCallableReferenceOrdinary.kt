// FIX: Use property access syntax
// COMPILER_ARGUMENTS: -XXLanguage:+ReferencesToSyntheticJavaProperties

fun main() {
    doSth(Foo::<caret>getFoo)
}

fun doSth(foo: (Foo) -> Int): Any = foo