// IS_APPLICABLE: false
// ERROR: Function 'foo' without a body must be abstract
// K2_ERROR: NON_ABSTRACT_FUNCTION_WITH_NO_BODY
open class A {
    fun <caret>foo(): Int
}