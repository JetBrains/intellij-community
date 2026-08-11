// "Add initializer" "false"
// ACTION: Add setter
// ACTION: Change to 'val'
// ACTION: Make internal
// ACTION: Make private
// ACTION: Make protected
// ACTION: Remove explicit type specification
// ERROR: Property must be initialized
// K2_AFTER_ERROR: EXTENSION_PROPERTY_MUST_HAVE_ACCESSORS_OR_BE_ABSTRACT
// K2_ERROR: EXTENSION_PROPERTY_MUST_HAVE_ACCESSORS_OR_BE_ABSTRACT
class A {
    <caret>var Int.n: Int
        get() = 1
}