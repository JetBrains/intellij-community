// FIR_IDENTICAL
class Outer {
    interface Inner1 {
        fun f() { }
    }

    interface Inner2 {
        fun g() { }
    }
}

class X : Outer.Inner1, Outer.Inner2 {
    <caret>
}

// MEMBER: "f(): Unit"
// MEMBER: "equals(other: Any?): Boolean"
// MEMBER: "hashCode(): Int"
// MEMBER: "toString(): String"
// MEMBER: "g(): Unit"