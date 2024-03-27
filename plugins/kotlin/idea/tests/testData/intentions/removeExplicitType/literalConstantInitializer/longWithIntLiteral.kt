// IGNORE_K2
fun test() {
    val x: <caret>Long = 1
    foo(x)
}

fun foo(x: Long) = x