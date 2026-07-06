// "Replace with safe (?.) call" "false"
// ACTION: Convert to block body
// ACTION: Enable option 'Function return types' for 'Types' inlay hints
// ACTION: Replace overloaded operator with function call
// ACTION: Wrap with '?.let { ... }' call
// ERROR: Reference has a nullable type '(String.() -> Unit)?', use explicit '?.invoke()' to make a function-like call instead
// K2_AFTER_ERROR: UNSAFE_IMPLICIT_INVOKE_CALL
// K2_ERROR: UNSAFE_IMPLICIT_INVOKE_CALL

fun foo(exec: (String.() -> Unit)?) = "".exec<caret>()
