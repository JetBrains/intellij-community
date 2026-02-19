// WITH_STDLIB
// IS_APPLICABLE: false
class A(val n: Int) {
    operator fun <caret>unaryMinus(): A = A(-n)
}

fun test() {
    val t = -A(1)
}