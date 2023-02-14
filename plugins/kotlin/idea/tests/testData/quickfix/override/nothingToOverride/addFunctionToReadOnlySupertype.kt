// "Add 'abstract fun f()' to 'Iterable'" "false"
// ACTION: Make internal
// ACTION: Make private
// ACTION: Make protected
// ACTION: Remove 'override' modifier
// ERROR: 'f' overrides nothing
// ERROR: Class 'B' is not abstract and does not implement abstract member public abstract operator fun iterator(): Iterator<Int> defined in kotlin.collections.Iterable
// WITH_STDLIB
class B : Iterable<Int> {
    <caret>override fun f() {}
}
