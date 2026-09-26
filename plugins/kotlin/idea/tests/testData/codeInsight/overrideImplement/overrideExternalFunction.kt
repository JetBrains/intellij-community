// FIR_IDENTICAL
open class A {
    open external fun foo()
}

class B : A() {
    <caret>
}

// MEMBER: "foo(): Unit"
// MEMBER: "equals(other: Any?): Boolean"
// MEMBER: "hashCode(): Int"
// MEMBER: "toString(): String"