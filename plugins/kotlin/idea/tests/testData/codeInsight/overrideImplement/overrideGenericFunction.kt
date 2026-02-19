// FIR_IDENTICAL
interface A<T> {
    fun foo(value : T) : Unit = println(value)
}

class C : A<C> {
    <caret>
}

// MEMBER: "foo(value: C): Unit"
// MEMBER: "equals(other: Any?): Boolean"
// MEMBER: "hashCode(): Int"
// MEMBER: "toString(): String"