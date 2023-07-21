import foo.A

class B : A {
    <caret>
}

// MEMBER: "equals(other: Any?): Boolean"
// MEMBER: "hashCode(): Int"
// MEMBER: "toString(): String"
// MEMBER_K2: "foo(): List<String?>?"
// MEMBER_K1: "foo(): (Mutable)List<String!>!"