// IS_APPLICABLE: false
// ERROR: Property must be initialized or be abstract
// K2_ERROR: Property must be initialized or be abstract.
open class A {
    val <caret>foo: Int
}