// "Make 'foo' 'abstract'" "false"
// ACTION: Add function body
// ACTION: Create test
// ACTION: Make internal
// ACTION: Make private
// ERROR: Function 'foo' must have a body
// K2_AFTER_ERROR: NON_MEMBER_FUNCTION_NO_BODY
// K2_ERROR: NON_MEMBER_FUNCTION_NO_BODY

<caret>fun foo()
