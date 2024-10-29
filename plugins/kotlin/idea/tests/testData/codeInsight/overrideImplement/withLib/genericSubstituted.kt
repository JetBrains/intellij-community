// FIR_IDENTICAL
package test

import dependency.D

class C: D<Int>() {
    <caret>
}

// MEMBER: "equals(other: Any?): Boolean"
// MEMBER: "hashCode(): Int"
// MEMBER: "id(t: Int): Int"
// MEMBER: "toString(): String"