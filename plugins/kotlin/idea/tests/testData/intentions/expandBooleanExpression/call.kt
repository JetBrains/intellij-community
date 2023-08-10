// AFTER-WARNING: Parameter 'b' is never used
fun test(b: Boolean): Boolean {
    return <caret>foo(b)
}

fun foo(b: Boolean) = true