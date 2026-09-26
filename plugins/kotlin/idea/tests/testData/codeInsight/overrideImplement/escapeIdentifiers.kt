// FIR_IDENTICAL
open class A {
    open fun foo(`object` : Any): Int = 0
}

class C : A() {
    <caret>
}

// MEMBER: "foo(`object`: Any): Int"
// MEMBER: "equals(other: Any?): Boolean"
// MEMBER: "hashCode(): Int"
// MEMBER: "toString(): String"