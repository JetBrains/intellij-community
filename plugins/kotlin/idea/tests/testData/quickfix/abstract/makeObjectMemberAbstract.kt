// "Make 'foo' 'abstract'" "false"
// ACTION: Add function body
// ACTION: Make internal
// ACTION: Make private
// ERROR: Function 'foo' without a body must be abstract
// K2_AFTER_ERROR: NON_ABSTRACT_FUNCTION_WITH_NO_BODY
// K2_ERROR: NON_ABSTRACT_FUNCTION_WITH_NO_BODY


object O {
    <caret>fun foo()
}
