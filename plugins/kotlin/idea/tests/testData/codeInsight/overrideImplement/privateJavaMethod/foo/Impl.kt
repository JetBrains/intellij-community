// FIR_IDENTICAL
interface I {
    fun z()
}

class C : A(), I {
    <caret>
}

// MEMBER: "equals(other: Any?): Boolean"
// MEMBER: "hashCode(): Int"
// MEMBER: "toString(): String"
// MEMBER: "x(): Unit"
// MEMBER: "z(): Unit"