// FIR_IDENTICAL
package foo

class Impl : Bar() {
    <caret>
}

// MEMBER: "f(): Any"
// MEMBER: "equals(other: Any?): Boolean"
// MEMBER: "hashCode(): Int"
// MEMBER: "toString(): String"