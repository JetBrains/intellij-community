// FIR_IDENTICAL
open class S {
    open fun s(vararg v: Int) {}
}

class D : S() {
    <caret>
}

// MEMBER: "s(vararg v: Int): Unit"
// MEMBER: "equals(other: Any?): Boolean"
// MEMBER: "hashCode(): Int"
// MEMBER: "toString(): String"