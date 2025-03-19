// IS_APPLICABLE: false
// K2_ERROR: Function 'foo' without a body must be abstract.
// ERROR: Function 'foo' without a body must be abstract
open class A {
    fun <caret>foo(): Int
}