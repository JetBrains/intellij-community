// FIX: Use property access syntax
// LANGUAGE_VERSION: 2.1

fun main() {
    doSth(Foo::<caret>getFoo)
}

fun doSth(foo: (Foo) -> Int): Any = foo