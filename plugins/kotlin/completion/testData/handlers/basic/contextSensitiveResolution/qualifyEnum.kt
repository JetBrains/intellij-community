// FIR_COMPARISON
// FIR_IDENTICAL

enum class Foo {
    FOO
}

fun Foo.foo(): Foo = FOO

fun test(): Foo = FOO.<caret>

// ELEMENT: foo
// IGNORE_K1
// COMPILER_ARGUMENTS: -Xcontext-sensitive-resolution