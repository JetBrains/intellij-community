// WITH_STDLIB
class A<T1, T2, T3>

typealias AIntListT1T2<T1, T2> = A<Int, List<T1>, T2>

fun <T1, T2> AIntListT1T2<T1, T2>.foo() = this

fun main() {
    A<Int, List<Int>, Int>().apply {<caret> foo() }
}
