// "Add initializer" "false"
// ACTION: Add getter
// ACTION: Make internal
// ACTION: Make private
// ERROR: Extension property must have accessors or be abstract
// K2_AFTER_ERROR: Extension property must have accessors or be abstract.
<caret>val Int.n: Int