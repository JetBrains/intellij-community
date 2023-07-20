import foo.A

class C : A() {
    <caret>
}

// MEMBER: "equals(other: Any?): Boolean"
// MEMBER: "hashCode(): Int"
// MEMBER: "toString(): String"
// MEMBER: "getAnswer(array: Array<(out) String!>!, number: Int, value: Any!): Int"