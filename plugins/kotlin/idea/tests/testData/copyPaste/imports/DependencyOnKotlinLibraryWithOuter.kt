// WITH_LIBRARY: ../KotlinLibrary

package a

import d.*
import d.E
import d.Outer.*

<selection>fun f(a: A, t: T) {
    g(A(c).ext())
    O1.f()
    O2
    E.ENTRY
}

fun f2(i: Outer.Inner, n: Outer.Nested, e: Outer.NestedEnum, o: Outer.NestedObj, t: Outer.NestedInterface, a: Outer.NestedAnnotation) {
    ClassObject
}</selection>