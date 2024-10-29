// FIR_IDENTICAL
open class A<T> {
}

interface I

class B : A<String>(), I {
    <caret>
}

// MEMBER: "equals(other: Any?): Boolean"
// MEMBER: "hashCode(): Int"
// MEMBER: "toString(): String"