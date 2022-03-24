package main.objects

class A<T>
typealias ArrayList<T> = A<T>
class B

class T {
    @Suppress("TOPLEVEL_TYPEALIASES_ONLY")
    typealias ArrayList<T> = B
    fun t(a: ArrayL<caret>)
}

// ELEMENT: ArrayList
// TAIL_TEXT: "<T> (main.objects)"