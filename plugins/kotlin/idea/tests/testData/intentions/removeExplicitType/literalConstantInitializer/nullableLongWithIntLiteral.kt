// IGNORE_K2
fun test() {
    var x: <caret>Long? = 1
    foo(x)
}

fun foo(x: Long?) = x