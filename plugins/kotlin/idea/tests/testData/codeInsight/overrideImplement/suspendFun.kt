// FIR_IDENTICAL
interface I {
    suspend fun foo()
}

class C : I {
    <caret>
}

// MEMBER: "foo(): Unit"
// MEMBER: "equals(other: Any?): Boolean"
// MEMBER: "hashCode(): Int"
// MEMBER: "toString(): String"