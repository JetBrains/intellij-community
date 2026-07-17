// COMPILER_ARGUMENTS: -Xcontext-parameters -XXLanguage:+ExplicitContextArguments
// IS_APPLICABLE: false
// ERROR: Type mismatch: inferred type is String but Array<String> was expected
// K2_ERROR: ARGUMENT_TYPE_MISMATCH

fun foo(vararg items: Array<String>) {}

fun main() {
    // wrong type mapping, so we don't suggest anything
    foo(<caret>*arrayOf("a", "b"))
}