// PROBLEM: none
expect abstract class Bar

expect class Foo : Bar {
    val baz: Int
}

actual abstract class Bar(baz: String)

actual class Foo(
    actual <caret>val baz: Int
) : Bar(baz.toString())