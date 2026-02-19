// ERROR: Type mismatch: inferred type is Unit but A was expected
package to

import a.*
import a.E.ENTRY
import a.Outer.Inner
import a.Outer.Nested
import a.Outer.NestedAnnotation
import a.Outer.NestedEnum
import a.Outer.NestedInterface
import a.Outer.NestedObj

fun f(p: A, t: T) {
    g(A(c).ext())
    O1.f()
    O2
    ENTRY
}

fun f2(i: Inner, n: Nested, e: NestedEnum, o: NestedObj, t: NestedInterface, a: NestedAnnotation) {
    ClassObject
}
