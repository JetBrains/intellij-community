// FIR_IDENTICAL
interface A {
    var Int.foo : Double
}

class B : A {
    <caret>
}

// MEMBER: "foo: Double"
// MEMBER: "equals(other: Any?): Boolean"
// MEMBER: "hashCode(): Int"
// MEMBER: "toString(): String"