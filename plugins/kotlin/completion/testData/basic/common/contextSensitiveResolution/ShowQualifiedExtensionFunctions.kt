
enum class Foo {
    FOO, BAR
}

fun Foo.foo(): Foo = FOO
fun Foo.bar(): Foo = BAR

fun test(): Foo = FOO.<caret>

// EXIST: foo
// EXIST: bar
// EXIST: apply
// EXIST: let
// COMPILER_ARGUMENTS: -Xcontext-sensitive-resolution