// FIR_IDENTICAL
open class Base<A, B, C>() {
    open val method : (A?) -> A = { it!! }
    open fun foo(value : B) : B = value
    open fun bar(value : () -> C) : (String) -> C = { value() }
}

class C : Base<String, C, Unit>() {
    <caret>
}

// MEMBER: "method: (String?) -> String"
// MEMBER: "foo(value: C): C"
// MEMBER: "bar(value: () -> Unit): (String) -> Unit"
// MEMBER: "equals(other: Any?): Boolean"
// MEMBER: "hashCode(): Int"
// MEMBER: "toString(): String"