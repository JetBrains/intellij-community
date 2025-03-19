// FIR_IDENTICAL
interface A {
    val String.prop : Int
}

class B : A {
    <caret>
}

// MEMBER_K2: "String.prop: Int"
// MEMBER_K1: "prop: Int"
// MEMBER: "equals(other: Any?): Boolean"
// MEMBER: "hashCode(): Int"
// MEMBER: "toString(): String"