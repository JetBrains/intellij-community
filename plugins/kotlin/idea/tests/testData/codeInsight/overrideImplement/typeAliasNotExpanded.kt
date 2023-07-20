// FIR_IDENTICAL
typealias Foo = Int

interface Bar {
    fun test(foo: Foo) = Unit
}

class Bar2 : Bar {
    <caret>
}

// MEMBER: "test(foo: Foo /* = Int */): Unit"
// MEMBER: "equals(other: Any?): Boolean"
// MEMBER: "hashCode(): Int"
// MEMBER: "toString(): String"