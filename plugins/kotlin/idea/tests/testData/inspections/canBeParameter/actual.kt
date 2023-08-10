expect abstract class Bar

expect class Foo : Bar {
    val baz: Int
}

actual abstract class Bar(baz: String)

actual class Foo(
    // NO
    actual val baz: Int
) : Bar(baz.toString())
