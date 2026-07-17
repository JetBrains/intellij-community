// "Add 'abstract fun f()' to 'Iterable'" "false"
// ACTION: Make internal
// ACTION: Make private
// ACTION: Make protected
// ACTION: Remove 'override' modifier
// ERROR: 'f' overrides nothing
// ERROR: Class 'B' is not abstract and does not implement abstract member public abstract operator fun iterator(): Iterator<Int> defined in kotlin.collections.Iterable
// WITH_STDLIB
// K2_AFTER_ERROR: ABSTRACT_MEMBER_NOT_IMPLEMENTED
// K2_AFTER_ERROR: NOTHING_TO_OVERRIDE
// K2_ERROR: ABSTRACT_MEMBER_NOT_IMPLEMENTED
// K2_ERROR: NOTHING_TO_OVERRIDE
class B : Iterable<Int> {
    <caret>override fun f() {}
}
