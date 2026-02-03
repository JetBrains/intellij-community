// FIR_COMPARISON
// FIR_IDENTICAL
enum class Foo{
    Bar
}

object Baz

fun foo(ba<caret>) {}

// ABSENT: { itemText: "bar: Foo.Bar" }
// ABSENT: { itemText: "baz: Baz" }
