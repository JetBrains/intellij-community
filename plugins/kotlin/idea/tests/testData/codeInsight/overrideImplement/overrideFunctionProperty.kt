// FIR_IDENTICAL
open class A() {
    open val method : () -> Unit? = {println("hello")}
}

fun some() : A {
    return object : A() {<caret>}
}

// TODO: need better selection and caret

// MEMBER: "method: () -> Unit?"
// MEMBER: "equals(other: Any?): Boolean"
// MEMBER: "hashCode(): Int"
// MEMBER: "toString(): String"