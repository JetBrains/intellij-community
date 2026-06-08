// COMPILER_ARGUMENTS: -Xcontext-parameters -XXLanguage:+ExplicitContextArguments
// PRIORITY: LOW
// INTENTION_TEXT: "Add 'x =' to argument"
// AFTER-WARNING: Parameter 'x' is never used
// AFTER-WARNING: Parameter 'y' is never used
// AFTER-WARNING: Parameter 'a' is never used

// K2_ERROR: Argument type mismatch: actual type is 'String', but 'Boolean' was expected.
// K2_ERROR: No context argument for 'x: String' found.
// K2_ERROR: Too many arguments for 'context(x: String) fun foo(a: Boolean, b: String): String'.
// K2_AFTER_ERROR: Mixing named and positional arguments is not allowed unless the order of the arguments matches the order of the parameters.
// K2_AFTER_ERROR: Mixing named and positional arguments is not allowed unless the order of the arguments matches the order of the parameters.
// K2_AFTER_ERROR: No value passed for parameter 'a'.
// K2_AFTER_ERROR: No value passed for parameter 'b'.

context(x: String)
fun foo(a: Boolean, b: String): String = ""

fun main() {
    // we don't suggest "b =" here because it's already mapped to "Hello", so the first available and type-correct is "x ="
    foo(<caret>"a", "Hello", "a")
}