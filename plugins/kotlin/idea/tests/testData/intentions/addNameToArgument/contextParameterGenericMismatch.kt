// COMPILER_ARGUMENTS: -Xcontext-parameters -XXLanguage:+ExplicitContextArguments
// IS_APPLICABLE: false
// K2_ERROR: ARGUMENT_PASSED_TWICE
// K2_ERROR: ARGUMENT_TYPE_MISMATCH
// K2_ERROR: NO_CONTEXT_ARGUMENT


context(x: List<String>)
fun foo(a: String): String = a

fun main() {
    // List<Int> does NOT match List<String> context param
    foo(<caret>listOf(1, 2), a = "World")
}
