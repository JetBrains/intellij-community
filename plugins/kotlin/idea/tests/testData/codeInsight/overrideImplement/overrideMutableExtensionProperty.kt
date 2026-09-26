// FIR_IDENTICAL
interface A {
    var Int.foo : Double
}

class B : A {
    <caret>
}

// MEMBER_K2: "Int.foo: Double"
// MEMBER_K1: "foo: Double"
// MEMBER: "equals(other: Any?): Boolean"
// MEMBER: "hashCode(): Int"
// MEMBER: "toString(): String"