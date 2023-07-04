// WITH_STDLIB
// AFTER-WARNING: Unreachable code
fun foo(): Foo = Foo(
    a = TODO(),
    b = TODO(),
    d = TODO(),
    e = TODO(),<caret>
)

data class Foo(
    val a: String,
    val b: String,
    val c: Int = 0,
    val d: Int,
    val e: String = "",
    val f: Boolean = true
)
