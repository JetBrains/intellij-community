import foo.A

class C : A() {
    <caret>
}

// MEMBER: "equals(other: Any?): Boolean"
// MEMBER: "hashCode(): Int"
// MEMBER: "toString(): String"
// MEMBER_K2: "getAnswer(array: Array<out String?>?, number: Int, value: Any?): Int"
// MEMBER_K1: "getAnswer(array: Array<(out) String!>!, number: Int, value: Any!): Int"