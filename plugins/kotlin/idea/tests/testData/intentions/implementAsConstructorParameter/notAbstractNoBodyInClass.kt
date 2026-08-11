// IS_APPLICABLE: false
// ERROR: Property must be initialized or be abstract
// K2_ERROR: MUST_BE_INITIALIZED_OR_BE_ABSTRACT
open class A {
    val <caret>foo: Int
}