// FIR_IDENTICAL
interface A {
    fun String.foo()
}

class B : A {
    <caret>
}

// MEMBER: "foo(): Unit"
// MEMBER: "equals(other: Any?): Boolean"
// MEMBER: "hashCode(): Int"
// MEMBER: "toString(): String"