// PROBLEM: none
// DISABLE_ERRORS
fun foo(vararg a: Int, b: Boolean) {}

fun test() {
    foo(1, 2, 3, true<caret>)
}
