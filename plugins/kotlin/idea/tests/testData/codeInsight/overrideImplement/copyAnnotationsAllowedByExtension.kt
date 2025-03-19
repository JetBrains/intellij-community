// FIR_IDENTICAL
annotation class AllowedAnnotation
annotation class UnknownAnnotation

open class ParentTarget {
    @AllowedAnnotation @UnknownAnnotation open fun targetFun() {}
}

class ChildTarget : ParentTarget() {
    <caret>
}

// MEMBER: "targetFun(): Unit"
// MEMBER: "equals(other: Any?): Boolean"
// MEMBER: "hashCode(): Int"
// MEMBER: "toString(): String"