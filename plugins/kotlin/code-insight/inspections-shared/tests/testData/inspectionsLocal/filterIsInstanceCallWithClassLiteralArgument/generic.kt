// WITH_STDLIB
// PROBLEM: none

class A<T, U>

fun foo(list: List<Any>) {
    list.<caret>filterIsInstance(A::class.java)
}