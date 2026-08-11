// IS_APPLICABLE: false
// ERROR: Abstract property 'foo' in non-abstract class 'A'
// K2_ERROR: ABSTRACT_PROPERTY_IN_NON_ABSTRACT_CLASS
object A {
    abstract val <caret>foo: Int
}