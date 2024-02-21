// ERROR: Type mismatch: inferred type is Unit but A was expected
package to

import d.A
import d.ClassObject
import d.E.ENTRY
import d.O1
import d.O2
import d.Outer.Inner
import d.Outer.Nested
import d.Outer.NestedAnnotation
import d.Outer.NestedEnum
import d.Outer.NestedInterface
import d.Outer.NestedObj
import d.T
import d.c
import d.ext
import d.g

fun f(a: A, t: T) {
    g(A(c).ext())
    O1.f()
    O2
    ENTRY
}

fun f2(i: Inner, n: Nested, e: NestedEnum, o: NestedObj, t: NestedInterface, a: NestedAnnotation) {
    ClassObject
}