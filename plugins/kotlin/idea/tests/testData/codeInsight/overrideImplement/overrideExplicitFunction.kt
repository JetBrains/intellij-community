// FIR_IDENTICAL
interface A {
    fun String.foo()
}

class B : A {
    <caret>
}

// MEMBER_K2: "String.foo(): Unit"
// MEMBER_K1: "foo(): Unit"
// MEMBER: "equals(other: Any?): Boolean"
// MEMBER: "hashCode(): Int"
// MEMBER: "toString(): String"