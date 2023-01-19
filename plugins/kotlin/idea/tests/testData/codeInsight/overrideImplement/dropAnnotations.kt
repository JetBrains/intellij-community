annotation class SomeAnnotation

open class ParentTarget {
    @SomeAnnotation open fun targetFun() {}
}

class ChildTarget : ParentTarget() {
    <caret>
}
