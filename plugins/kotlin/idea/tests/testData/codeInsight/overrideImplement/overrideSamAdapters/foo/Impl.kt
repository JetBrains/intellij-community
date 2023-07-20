// FIR_IDENTICAL
package foo

class Impl: B() {
    <caret>
}

// MEMBER: "equals(other: Any?): Boolean"
// MEMBER: "hashCode(): Int"
// MEMBER: "toString(): String"
// MEMBER: "foo(r: Runnable!): Unit"