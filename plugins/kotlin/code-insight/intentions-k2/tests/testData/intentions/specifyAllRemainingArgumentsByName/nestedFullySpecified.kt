// IS_APPLICABLE: false
// SKIP_ERRORS_BEFORE
fun foo(a: Int, b: Int): Int = 1

fun test() {
    foo(foo(1, 2<caret>))
}