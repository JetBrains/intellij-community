// WITH_STDLIB
// PROBLEM: none

class A {
    val NaN = 0.3
}

fun test() {
    val a = A()
    val t = a.NaN ==<caret> 0.5
}
