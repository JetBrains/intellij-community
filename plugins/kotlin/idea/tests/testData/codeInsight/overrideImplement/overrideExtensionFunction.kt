// FIR_IDENTICAL
open class A {
    open fun Int.foo(): Int {
        return 0
    }
}

class B: A() {
    <caret>
}

// MEMBER: "foo(): Int"
// MEMBER: "equals(other: Any?): Boolean"
// MEMBER: "hashCode(): Int"
// MEMBER: "toString(): String"