// IS_APPLICABLE: false
// ERROR: Abstract function 'foo' in non-abstract class 'A'
// K2_ERROR: ABSTRACT_FUNCTION_IN_NON_ABSTRACT_CLASS
object A {
    abstract fun <caret>foo(): Int
}