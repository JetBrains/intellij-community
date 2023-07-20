package foo

import foo.Intf

class Impl(): Intf() {
    <caret>
}

// MEMBER: "equals(other: Any?): Boolean"
// MEMBER: "hashCode(): Int"
// MEMBER: "toString(): String"
// MEMBER: "getFooBar(): String!"