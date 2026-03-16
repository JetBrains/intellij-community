// FIR_COMPARISON
// FIR_IDENTICAL

enum class Foo {
    FOO
}

fun Foo.foo(): Foo = FOO

fun test(): Foo = Foo.FOO.<caret>

// ELEMENT: foo
// COMPILER_ARGUMENTS: -Xcontext-sensitive-resolution