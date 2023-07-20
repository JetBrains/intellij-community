import foo.A

class B : A {
    <caret>
}

// MEMBER: "equals(other: Any?): Boolean"
// MEMBER: "hashCode(): Int"
// MEMBER: "toString(): String"
// MEMBER: "foo(): (Mutable)List<String!>!"