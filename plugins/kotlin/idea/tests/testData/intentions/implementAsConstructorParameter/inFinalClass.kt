// IS_APPLICABLE: false
// ERROR: Abstract property 'foo' in non-abstract class 'A'
// K2-ERROR: Abstract property 'foo' in non-abstract class 'A'.
class A {
    abstract val <caret>foo: Int
}