interface I {
    open fun foo(){}
}

open class A {
    open fun foo(){}
}

class C : A(), I {
    <caret>
}

// MEMBER: "foo(): Unit"
// MEMBER: "equals(other: Any?): Boolean"
// MEMBER: "hashCode(): Int"
// MEMBER: "toString(): String"