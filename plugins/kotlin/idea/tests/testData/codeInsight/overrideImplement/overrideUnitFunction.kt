// FIR_IDENTICAL
interface A {
    fun foo(value : String) : Unit {}
}

class C : A {
  <caret>
}

// MEMBER: "foo(value: String): Unit"
// MEMBER: "equals(other: Any?): Boolean"
// MEMBER: "hashCode(): Int"
// MEMBER: "toString(): String"