// FIR_COMPARISON
// FIR_IDENTICAL
package main.objects

class A<T>
typealias ArrayList<T> = A<T>
class B

class T {
    @Suppress("TOPLEVEL_TYPEALIASES_ONLY")
    typealias ArrayList<T> = B
    fun t(a: ArrayL<caret>)
}

// INVOCATION_COUNT: 2
// ELEMENT: ArrayList
// TAIL_TEXT: "<E> (java.util)"