// WITH_STDLIB
// IS_APPLICABLE: false
// ERROR: Abstract property 'foo' in non-abstract class 'Test'
// K2_ERROR: Abstract property 'foo' in non-abstract class 'Test'.

object Test {
    abstract val <caret>foo: Int
}