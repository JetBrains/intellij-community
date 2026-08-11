package test

import dependency.Outer // unused
import dependency.TypeAlias1
import dependency.TypeAlias2 // unused
import dependency.TypeAlias3
import dependency.TypeAlias4
import dependency.TypeAlias5 // unused
import dependency.TypeAlias6 // unused
import dependency.TypeAlias7

fun testConstructorCall1() {
    TypeAlias1()
}

fun testConstructorCall2() {
    dependency.TypeAlias2()
}

fun testCallableReference() {
    ::TypeAlias3
}

fun testClassReference1() {
    TypeAlias4::class
}

fun testClassReference2() {
    dependency.TypeAlias5::class
}

fun testTypeRefs(
    p1: dependency.TypeAlias6,
    p2: TypeAlias7, 
) {}