// FIR_IDENTICAL
// DISABLE-ERRORS
interface A {
    fun f(x: Iterable<IADW>.() -> PPPP): XXX<Int, YYY>
}

class X : A {
    <caret>
}

// MEMBER: "f(x: Iterable<IADW>.() -> PPPP): XXX<Int, YYY>"
// MEMBER: "equals(other: Any?): Boolean"
// MEMBER: "hashCode(): Int"
// MEMBER: "toString(): String"