// AFTER-WARNING: Variable 'i' is never used
fun foo(n: Int?): Int? = n

fun test() {
    var i: <caret>Int? = foo(1)
}