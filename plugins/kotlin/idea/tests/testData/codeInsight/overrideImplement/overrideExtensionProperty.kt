// FIR_IDENTICAL
interface A {
    val String.prop : Int
}

class B : A {
    <caret>
}

// MEMBER: "prop: Int"
// MEMBER: "equals(other: Any?): Boolean"
// MEMBER: "hashCode(): Int"
// MEMBER: "toString(): String"