fun foo(): Foo = Foo(
    a = TODO(),
    b = TODO(),
    d = TODO(),<caret>
)

data class Foo(
    val a: String,
    val b: String,
    val c: Int = 0,
    val d: Int
)
