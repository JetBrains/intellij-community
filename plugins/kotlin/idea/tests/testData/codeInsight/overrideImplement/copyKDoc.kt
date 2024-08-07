// FIR_IDENTICAL
// COPY_DOC
abstract class A {
    /**
     * @see TEST
     */
    abstract fun foo()
}

class B : A() {
    <caret>
}

// MEMBER: "foo(): Unit"
// MEMBER: "equals(other: Any?): Boolean"
// MEMBER: "hashCode(): Int"
// MEMBER: "toString(): String"