// FIR_IDENTICAL
open class A {
    open fun foo() {}
}

interface B {
    fun bar()
}

class C : A(), B {
   <caret>
}

// MEMBER: "foo(): Unit"
// MEMBER: "equals(other: Any?): Boolean"
// MEMBER: "hashCode(): Int"
// MEMBER: "toString(): String"
// MEMBER: "bar(): Unit"