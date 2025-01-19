// IS_APPLICABLE: false
// K2-ERROR: Abstract property 'foo' in non-abstract class 'A'.
// ERROR: Abstract property 'foo' in non-abstract class 'A'
class A {
    abstract val <caret>foo: Int
}