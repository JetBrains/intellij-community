package to

import a.Outer
import a.Outer.Inner
import a.Outer.Nested
import a.Outer.NestedAnnotation
import a.Outer.NestedEnum
import a.Outer.NestedInterface
import a.Outer.NestedObj

fun f(i: Inner, n: Nested, e: NestedEnum, o: NestedObj, t: NestedInterface, aa: NestedAnnotation) {
    Outer().Inner2()
}
