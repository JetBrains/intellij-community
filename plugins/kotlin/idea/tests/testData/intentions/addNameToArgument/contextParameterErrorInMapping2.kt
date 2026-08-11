// COMPILER_ARGUMENTS: -Xcontext-parameters -XXLanguage:+ExplicitContextArguments
// PRIORITY: LOW
// INTENTION_TEXT: "Add 'x =' to argument"
// AFTER-WARNING: Parameter 'x' is never used
// AFTER-WARNING: Parameter 'y' is never used
// AFTER-WARNING: Parameter 'a' is never used
// K2_AFTER_ERROR: MIXING_NAMED_AND_POSITIONAL_ARGUMENTS
// K2_AFTER_ERROR: MIXING_NAMED_AND_POSITIONAL_ARGUMENTS
// K2_AFTER_ERROR: NO_VALUE_FOR_PARAMETER
// K2_AFTER_ERROR: NO_VALUE_FOR_PARAMETER
// K2_ERROR: ARGUMENT_TYPE_MISMATCH
// K2_ERROR: NO_CONTEXT_ARGUMENT
// K2_ERROR: TOO_MANY_ARGUMENTS


context(x: String)
fun foo(a: Boolean, b: String): String = ""

fun main() {
    // we don't suggest "b =" here because it's already mapped to "Hello", so the first available and type-correct is "x ="
    foo(<caret>"a", "Hello", "a")
}