// "Make 'foo' 'abstract'" "false"
// ACTION: Add function body
// ACTION: Create test
// ACTION: Make internal
// ACTION: Make private
// ERROR: Function 'foo' must have a body
// K2_AFTER_ERROR: Function 'foo' must have a body.

<caret>fun foo()
