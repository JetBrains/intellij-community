// "Add initializer" "false"
// ACTION: Add getter
// ACTION: Make internal
// ACTION: Make private
// ACTION: Make protected
// ACTION: Move to constructor
// ERROR: Extension property must have accessors or be abstract
// K2_AFTER_ERROR: EXTENSION_PROPERTY_MUST_HAVE_ACCESSORS_OR_BE_ABSTRACT
// K2_ERROR: EXTENSION_PROPERTY_MUST_HAVE_ACCESSORS_OR_BE_ABSTRACT
class A {
    <caret>val Int.n: Int
}