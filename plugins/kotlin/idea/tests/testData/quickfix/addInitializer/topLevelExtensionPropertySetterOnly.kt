// "Add initializer" "false"
// ACTION: Add getter
// ACTION: Make internal
// ACTION: Make private
// ERROR: Extension property must have accessors or be abstract
// K2_AFTER_ERROR: EXTENSION_PROPERTY_MUST_HAVE_ACCESSORS_OR_BE_ABSTRACT
// K2_ERROR: EXTENSION_PROPERTY_MUST_HAVE_ACCESSORS_OR_BE_ABSTRACT
<caret>val Int.n: Int