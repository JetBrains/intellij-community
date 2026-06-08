// COMPILER_ARGUMENTS: -Xcontext-parameters -XXLanguage:+ExplicitContextArguments
// PRIORITY: LOW
// INTENTION_TEXT: "Add 'x =' to argument"
// AFTER-WARNING: Parameter 'x' is never used
// AFTER-WARNING: Parameter 'y' is never used
// AFTER-WARNING: Parameter 'a' is never used

// K2_AFTER_ERROR: Mixing named and positional arguments is not allowed unless the order of the arguments matches the order of the parameters.
// K2_AFTER_ERROR: No context argument for 'y: String' found.
// K2_ERROR: Argument already passed for this parameter.
// K2_ERROR: Argument type mismatch: actual type is 'String', but 'Int' was expected.
// K2_ERROR: No context argument for 'x: String' found.
// K2_ERROR: No context argument for 'y: String' found.
// K2_ERROR: Too many arguments for 'context(x: String, y: String) fun foo(a: Int): Int'.

context(x: String, y: String)
fun foo(a: Int): Int = a

fun main() {
    // Both context params have the same type, should match by position
    // First String -> x, Second String -> y
    foo(<caret>"First", "Second", a = 42)
}
