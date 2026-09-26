// FIR_IDENTICAL
interface T {
    fun foo()
    fun bar()
}

class C(t :T) : T by t {
    <caret>
}

// KT-5103

// MEMBER: "foo(): Unit"
// MEMBER: "bar(): Unit"
// MEMBER: "equals(other: Any?): Boolean"
// MEMBER: "hashCode(): Int"
// MEMBER: "toString(): String"