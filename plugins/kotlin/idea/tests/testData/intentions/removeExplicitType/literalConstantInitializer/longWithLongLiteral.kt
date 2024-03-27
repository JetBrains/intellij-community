fun test() {
    val x: <caret>Long = 1L
    foo(x)
}

fun foo(x: Long) = x