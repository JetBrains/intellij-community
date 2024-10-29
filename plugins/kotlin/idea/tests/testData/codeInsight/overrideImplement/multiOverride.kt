// FIR_IDENTICAL
interface A {
    fun foo(value : String) : Int = 0
    fun bar() : String = "hello"
}

class C : A {
    <caret>
}

// MEMBER: "foo(value: String): Int"
// MEMBER: "bar(): String"
// MEMBER: "equals(other: Any?): Boolean"
// MEMBER: "hashCode(): Int"
// MEMBER: "toString(): String"