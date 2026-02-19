// WITH_STDLIB
// PROBLEM: none

package pack

class A<T, U>

fun foo(list: List<Any>) {
    list.<caret>filterIsInstance(pack.A::class.java)
}