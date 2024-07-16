// PROBLEM: none
fun foo(n: Int)  {}

fun Any.test() {
    <caret>if (this is String) {
        foo(length)
    } else null
}
