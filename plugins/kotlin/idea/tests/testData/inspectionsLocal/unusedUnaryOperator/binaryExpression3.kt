// PROBLEM: none
fun test(b: Boolean) {
    var x = 0
    x = foo(if (b) <caret>-1 else -2, -3)
}

fun foo(i: Int, j: Int) = i + j