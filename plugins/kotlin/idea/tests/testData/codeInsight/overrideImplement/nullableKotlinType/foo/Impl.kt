// FIR_IDENTICAL
interface IBase {
    fun foo(): Any?
}

class C : IBase {
    <caret>
}

// MEMBER: "foo(): Any?"
// MEMBER: "equals(other: Any?): Boolean"
// MEMBER: "hashCode(): Int"
// MEMBER: "toString(): String"