// WITH_STDLIB
// AFTER-WARNING: Parameter 'i' is never used
fun foo(i: Int) {}

fun test(s: String) {
    <caret>if (!s.isNotEmpty()) {
        foo(1)
    } else {
        foo(2)
    }
}