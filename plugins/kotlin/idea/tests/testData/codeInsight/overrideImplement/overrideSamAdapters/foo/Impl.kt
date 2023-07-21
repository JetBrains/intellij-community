// FIR_IDENTICAL
package foo

class Impl: B() {
    <caret>
}

// MEMBER: "equals(other: Any?): Boolean"
// MEMBER: "hashCode(): Int"
// MEMBER: "toString(): String"
// MEMBER_K2: "foo(r: Runnable?): Unit"
// MEMBER_K1: "foo(r: Runnable!): Unit"