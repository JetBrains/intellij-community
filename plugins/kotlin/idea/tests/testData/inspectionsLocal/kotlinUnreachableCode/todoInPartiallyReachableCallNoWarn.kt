
// PROBLEM: none
fun foo(a: Int, b: Int) {}
fun foo(a: String, b: String, c: String) {}
fun foo(a: Int, b: Int, c: Int, d: Int) {}

fun test() {
    foo(
        <caret>a = "",
        b = TODO(),
        c = TODO()
    )
}
