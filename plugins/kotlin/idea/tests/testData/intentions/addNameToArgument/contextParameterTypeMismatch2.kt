// COMPILER_ARGUMENTS: -Xcontext-parameters -XXLanguage:+ExplicitContextArguments
// PRIORITY: LOW
// INTENTION_TEXT: "Add 'x =' to argument"
// AFTER-WARNING: Parameter 'x' is never used
// AFTER-WARNING: Parameter 'y' is never used
// AFTER-WARNING: Parameter 'a' is never used

// K2_ERROR: Argument already passed for this parameter.
// K2_ERROR: Argument type mismatch: actual type is 'Int', but 'String' was expected.
// K2_ERROR: No context argument for 'x: String' found.
// K2_ERROR: No context argument for 'y: Int' found.
// K2_ERROR: Too many arguments for 'context(x: String, y: Int) fun foo(a: String): String'.
// K2_AFTER_ERROR: Argument already passed for this parameter.
// K2_AFTER_ERROR: Argument type mismatch: actual type is 'Int', but 'String' was expected.
// K2_AFTER_ERROR: No context argument for 'y: Int' found.

context(x: String, y: Int)
fun foo(a: String): String = x + y + a

fun main() {
    // "Hello" is String and our best effort is to suggest "x"
    foo(42, <caret>"Hello", a = "World")
}