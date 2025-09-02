// NAME_COUNT_TO_USE_STAR_IMPORT: 100
package test

import dependency.Outer
import dependency.TypeAlias1
import dependency.TypeAlias2
import dependency.TypeAlias3
import dependency.TypeAlias4
import dependency.TypeAlias5
import dependency.TypeAlias6
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
    p1: TypeAlias6,
    p2: dependency.TypeAlias7,
) {}