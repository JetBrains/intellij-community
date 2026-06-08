// COMPILER_ARGUMENTS: -Xcontext-parameters -XXLanguage:+ExplicitContextArguments
// PRIORITY: LOW
// INTENTION_TEXT: "Add 'x =' to argument"
// AFTER-WARNING: Parameter 'x' is never used
// AFTER-WARNING: Parameter 'y' is never used
// AFTER-WARNING: Parameter 'a' is never used

// K2_ERROR: Argument already passed for this parameter.
// K2_ERROR: No context argument for 'x: String' found.
// K2_ERROR: No context argument for 'y: Int' found.
// K2_ERROR: Too many arguments for 'context(x: String, y: Int) fun foo(a: String): String'.
// K2_AFTER_ERROR: Mixing named and positional arguments is not allowed unless the order of the arguments matches the order of the parameters.
// K2_AFTER_ERROR: No context argument for 'y: Int' found.

context(x: String, y: Int)
fun foo(a: String): String = a

fun main() {
    // First arg "Hello" matches x: String, but second arg "World" does NOT match y: Int
    // The intention should still be applicable for the first argument
    foo(<caret>"Hello", "World", a = "!")
}
