// AFTER-WARNING: Parameter 'a' is never used
// AFTER-WARNING: Parameter 'b' is never used
// AFTER-WARNING: Parameter 'b' is never used
// AFTER-WARNING: Parameter 'c' is never used
// AFTER-WARNING: Parameter 'c' is never used
fun foo(a: Int = 1, b: Int = 1, c: Int = 1) {}

fun bar(a: Int, b: Int, c: Int) {
    foo(<caret>
        a
    )
}