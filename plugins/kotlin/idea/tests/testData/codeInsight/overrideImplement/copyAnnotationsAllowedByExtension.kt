// FIR_IDENTICAL
annotation class AllowedAnnotation
annotation class UnknownAnnotation

open class ParentTarget {
    @AllowedAnnotation @UnknownAnnotation open fun targetFun() {}
}

class ChildTarget : ParentTarget() {
    <caret>
}
