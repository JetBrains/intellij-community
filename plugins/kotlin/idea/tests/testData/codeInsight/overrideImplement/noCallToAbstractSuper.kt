// FIR_IDENTICAL
abstract class A {
    abstract override fun hashCode(): Int
}

interface I

class B : A(), I {  // Any:hashCode() (available via I) is not an option
    <caret>
}

// MEMBER: "hashCode(): Int"
// MEMBER: "equals(other: Any?): Boolean"
// MEMBER: "toString(): String"