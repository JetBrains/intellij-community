// WITH_STDLIB
// ERROR: No value passed for parameter 'a'
// ERROR: No value passed for parameter 'b'
// ERROR: No value passed for parameter 'd'
// ERROR: No value passed for parameter 'e'
// SKIP_ERRORS_AFTER
// AFTER-WARNING: Unreachable code
fun foo(): Foo = Foo(<caret>)

data class Foo(
    val a: String,
    val b: String,
    val c: Int = 0,
    val d: Int,
    val e: Boolean
)
