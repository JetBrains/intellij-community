// FIR_IDENTICAL
// DISABLE_ERRORS

@RequiresOptIn
annotation class FirstExperience

open class ParentTarget {
    @FirstExperience open fun targetFun() {}
}

class ChildTarget : ParentTarget() {
    <caret>
}

// MEMBER: "targetFun(): Unit"
// MEMBER: "equals(other: Any?): Boolean"
// MEMBER: "hashCode(): Int"
// MEMBER: "toString(): String"