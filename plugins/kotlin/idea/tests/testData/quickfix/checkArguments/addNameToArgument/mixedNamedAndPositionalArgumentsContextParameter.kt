// "Add name to argument: 'a = "Hello"'" "true"

// LANGUAGE_VERSION: 2.2
// COMPILER_ARGUMENTS: -Xcontext-parameters
// K2_ERROR: Mixing named and positional arguments is not allowed unless the order of the arguments matches the order of the parameters.
// K2_ERROR: Mixing named and positional arguments is not allowed unless the order of the arguments matches the order of the parameters.
// K2_ERROR: No context argument for 'x: String' found.
// K2_ERROR: No value passed for parameter 'a'.
// K2_AFTER_ERROR: Mixing named and positional arguments is not allowed unless the order of the arguments matches the order of the parameters.
// K2_AFTER_ERROR: No context argument for 'x: String' found.

context(x: String)
fun foo(a: String, b: String): String = x + a + b

fun main() {
    foo(b = "World", <caret>"Hello", "ctx")
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddNameToArgumentFixFactory$AddNameToArgumentFix