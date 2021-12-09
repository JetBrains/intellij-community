package main.objects

import main.objects.B.ArrayList

class A<T>
typealias ArrayList<T> = A<T>

class B {
    class ArrayList
}
class T {
    @Suppress("TOPLEVEL_TYPEALIASES_ONLY")
    typealias ArrayList<T> = B
    fun t(a: Arr<caret>)
}

// ELEMENT: ArrayList
// TAIL_TEXT: "<T> (main.objects)"