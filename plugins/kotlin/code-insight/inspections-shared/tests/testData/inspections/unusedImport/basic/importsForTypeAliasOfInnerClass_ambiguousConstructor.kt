package test

import dependency_ambiguousConstructor.Outer
import dependency_ambiguousConstructor.Outer.Inner // unused
import dependency_ambiguousConstructor.InnerTypeAlias

fun testConstructorCall(outer: Outer) {
    outer.InnerTypeAlias()
}
