// WITH_STDLIB
// PROBLEM: none

class A(val l: Any)

fun foo(x: A): Any = TODO()
fun bar() {
    listOf<A>().<caret>mapNotNull { foo(it) as? String }
}