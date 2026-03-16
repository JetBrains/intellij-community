// WITH_STDLIB
// PROBLEM: none

interface X
class A(val l: Any) { fun foo(): Any = TODO() }

internal fun bar() {
    listOf<A>().<caret>mapNotNull { it.l as? X }
}