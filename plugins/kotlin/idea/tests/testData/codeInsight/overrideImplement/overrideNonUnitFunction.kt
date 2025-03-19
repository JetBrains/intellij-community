// FIR_IDENTICAL
interface A {
    fun foo(value : String) : Int = 0
}

class C : A {
  <caret>
}

// MEMBER: "foo(value: String): Int"
// MEMBER: "equals(other: Any?): Boolean"
// MEMBER: "hashCode(): Int"
// MEMBER: "toString(): String"