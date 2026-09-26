
enum class Foo {
    FOO, BAR
}

fun Foo.foo(): Foo = Foo.FOO
fun Foo.bar(): Foo = Foo.BAR

fun test(): Foo = FOO.<caret>

// ABSENT: FOO
// ABSENT: BAR