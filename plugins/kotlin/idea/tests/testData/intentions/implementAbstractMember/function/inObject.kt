// IS_APPLICABLE: false
// K2_ERROR: Abstract function 'foo' in non-abstract class 'A'.
// ERROR: Abstract function 'foo' in non-abstract class 'A'
object A {
    abstract fun <caret>foo(): Int
}