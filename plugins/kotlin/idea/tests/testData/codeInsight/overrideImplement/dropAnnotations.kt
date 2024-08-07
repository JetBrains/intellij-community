// FIR_IDENTICAL
annotation class SomeAnnotation

open class ParentTarget {
    @SomeAnnotation open fun targetFun() {}
}

class ChildTarget : ParentTarget() {
    <caret>
}

// MEMBER: "targetFun(): Unit"
// MEMBER: "equals(other: Any?): Boolean"
// MEMBER: "hashCode(): Int"
// MEMBER: "toString(): String"