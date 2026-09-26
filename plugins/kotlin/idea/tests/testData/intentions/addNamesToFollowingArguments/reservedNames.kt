// COMPILER_ARGUMENTS: -Xcontext-parameters -XXLanguage:+ExplicitContextArguments
// K2_ERROR: MIXING_NAMED_AND_POSITIONAL_ARGUMENTS
// K2_ERROR: MIXING_NAMED_AND_POSITIONAL_ARGUMENTS
// K2_ERROR: NO_CONTEXT_ARGUMENT
// K2_ERROR: NO_VALUE_FOR_PARAMETER

// PRIORITY: LOW

context(x: String)
fun foo(a: String, b: String): String {
    return x + a
}

fun main() {
    foo(b = "World", <caret>"Hello", "a")
}