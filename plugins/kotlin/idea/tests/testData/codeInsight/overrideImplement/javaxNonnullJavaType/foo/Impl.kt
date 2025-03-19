// FIR_IDENTICAL
import foo.A

class B : A() {
    <caret>
}

// MEMBER: "equals(other: Any?): Boolean"
// MEMBER: "hashCode(): Int"
// MEMBER: "toString(): String"
// MEMBER: "foo(s: String): String"