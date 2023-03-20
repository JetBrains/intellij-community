// "Replace with safe (?.) call" "false"
// ACTION: Convert to block body
// ACTION: Replace overloaded operator with function call
// ACTION: Wrap with '?.let { ... }' call
// ERROR: Reference has a nullable type 'String?', use explicit '?.invoke()' to make a function-like call instead

fun String?.foo(exec: (String.() -> Unit)) = exec<caret>()