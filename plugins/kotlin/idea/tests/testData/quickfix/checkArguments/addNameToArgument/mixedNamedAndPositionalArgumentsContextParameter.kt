// "Add name to argument: 'a = "Hello"'" "true"
// K2_AFTER_ERROR: MIXING_NAMED_AND_POSITIONAL_ARGUMENTS
// K2_AFTER_ERROR: NO_CONTEXT_ARGUMENT
// K2_ERROR: MIXING_NAMED_AND_POSITIONAL_ARGUMENTS
// K2_ERROR: MIXING_NAMED_AND_POSITIONAL_ARGUMENTS
// K2_ERROR: NO_CONTEXT_ARGUMENT
// K2_ERROR: NO_VALUE_FOR_PARAMETER

// LANGUAGE_VERSION: 2.2
// COMPILER_ARGUMENTS: -Xcontext-parameters

context(x: String)
fun foo(a: String, b: String): String = x + a + b

fun main() {
    foo(b = "World", <caret>"Hello", "ctx")
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddNameToArgumentFixFactory$AddNameToArgumentFix