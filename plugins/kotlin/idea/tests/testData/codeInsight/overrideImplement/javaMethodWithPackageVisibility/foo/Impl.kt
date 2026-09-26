package foo

import foo.Intf

class Impl(): Intf() {
    <caret>
}

// MEMBER: "equals(other: Any?): Boolean"
// MEMBER: "hashCode(): Int"
// MEMBER: "toString(): String"
// MEMBER_K2: "getFooBar(): String?"
// MEMBER_K1: "getFooBar(): String!"