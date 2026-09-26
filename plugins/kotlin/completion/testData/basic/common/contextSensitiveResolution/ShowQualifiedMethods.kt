
enum class Foo {
    FOO, BAR

    fun foo(): Foo = FOO
    fun bar(): Foo = BAR
}


fun test(): Foo = FOO.<caret>

// EXIST: foo
// EXIST: bar
// COMPILER_ARGUMENTS: -Xcontext-sensitive-resolution