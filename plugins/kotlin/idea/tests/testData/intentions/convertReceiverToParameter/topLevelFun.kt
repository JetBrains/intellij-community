fun <caret>String.foo(n: Int): Boolean {
    return length - n/2 > 1
}

fun test() {
    "1".foo(2)
}