
enum class Foo {
    FOO, BAR

    val foo: Foo = FOO
    val bar: Foo = BAR
}


fun test(): Foo = FOO.<caret>

// EXIST: foo
// EXIST: bar
// COMPILER_ARGUMENTS: -Xcontext-sensitive-resolution