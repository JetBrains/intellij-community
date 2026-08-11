// COMPILER_ARGUMENTS: -Xcontext-parameters -XXLanguage:+ExplicitContextArguments
// PRIORITY: LOW
// INTENTION_TEXT: "Add 'x =' to argument"
// AFTER-WARNING: Parameter 'x' is never used
// AFTER-WARNING: Parameter 'y' is never used
// AFTER-WARNING: Parameter 'a' is never used
// K2_AFTER_ERROR: MIXING_NAMED_AND_POSITIONAL_ARGUMENTS
// K2_AFTER_ERROR: NO_CONTEXT_ARGUMENT
// K2_ERROR: ARGUMENT_PASSED_TWICE
// K2_ERROR: NO_CONTEXT_ARGUMENT
// K2_ERROR: NO_CONTEXT_ARGUMENT
// K2_ERROR: TOO_MANY_ARGUMENTS


context(x: String, y: Int)
fun foo(a: String): String = a

fun main() {
    // First arg "Hello" matches x: String, but second arg "World" does NOT match y: Int
    // The intention should still be applicable for the first argument
    foo(<caret>"Hello", "World", a = "!")
}
