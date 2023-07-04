// IS_APPLICABLE: false
fun foo(): Foo = Foo(<caret>)

data class Foo(
    val a: String = "a",
    val b: String = "b",
    val c: Int = 0,
    val d: String = "d"
)