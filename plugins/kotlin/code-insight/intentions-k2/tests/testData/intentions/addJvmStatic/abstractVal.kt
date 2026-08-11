// WITH_STDLIB
// IS_APPLICABLE: false
// ERROR: Abstract property 'foo' in non-abstract class 'Test'
// K2_ERROR: ABSTRACT_PROPERTY_IN_NON_ABSTRACT_CLASS

object Test {
    abstract val <caret>foo: Int
}