// COMPILER_ARGUMENTS: -Xcontext-parameters -XXLanguage:+ExplicitContextArguments
// PRIORITY: LOW
// INTENTION_TEXT: "Add 'x =' to argument"
// AFTER-WARNING: Parameter 'x' is never used
// AFTER-WARNING: Parameter 'y' is never used
// AFTER-WARNING: Parameter 'a' is never used
// K2_AFTER_ERROR: MIXING_NAMED_AND_POSITIONAL_ARGUMENTS
// K2_AFTER_ERROR: NO_CONTEXT_ARGUMENT
// K2_ERROR: ARGUMENT_PASSED_TWICE
// K2_ERROR: ARGUMENT_TYPE_MISMATCH
// K2_ERROR: NO_CONTEXT_ARGUMENT
// K2_ERROR: NO_CONTEXT_ARGUMENT
// K2_ERROR: TOO_MANY_ARGUMENTS


context(x: String, y: String)
fun foo(a: Int): Int = a

fun main() {
    // Both context params have the same type, should match by position
    // First String -> x, Second String -> y
    foo(<caret>"First", "Second", a = 42)
}
