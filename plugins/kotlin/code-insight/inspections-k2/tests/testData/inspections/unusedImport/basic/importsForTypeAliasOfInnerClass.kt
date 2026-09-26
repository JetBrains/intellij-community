package test

import dependency.Outer
import dependency.Outer.Inner // unused
import dependency.InnerTypeAlias1
import dependency.InnerTypeAlias2
import dependency.InnerTypeAlias3
import dependency.InnerTypeAlias4
import dependency.InnerTypeAlias5
import dependency.InnerTypeAlias6
import dependency.InnerTypeAlias7
import dependency.InnerTypeAlias8 // unused
import dependency.InnerTypeAlias9
import dependency.InnerTypeAlias10 // unused

fun testConstructorCall1(outer: Outer) {
    outer.InnerTypeAlias1()
}

fun testConstructorCall2() {
    Outer().InnerTypeAlias2()
}

fun Outer.testConstructorCall3() {
    InnerTypeAlias3()
}

fun testCallableReference1(outer: Outer) {
    outer::InnerTypeAlias4
}

fun testCallableReference2() {
    Outer()::InnerTypeAlias5
}

fun Outer.testCallableReference3() {
    ::InnerTypeAlias6
}

fun testClassReference1() {
    InnerTypeAlias7::class
}

fun testClassReference2() {
    dependency.InnerTypeAlias8::class
}

fun testTypeRefs(
    p1: InnerTypeAlias9,
    p2: dependency.InnerTypeAlias10, 
) {}