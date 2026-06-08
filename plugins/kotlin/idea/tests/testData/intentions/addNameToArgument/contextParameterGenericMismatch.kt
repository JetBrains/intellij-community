// COMPILER_ARGUMENTS: -Xcontext-parameters -XXLanguage:+ExplicitContextArguments
// IS_APPLICABLE: false

// K2_ERROR: Argument already passed for this parameter.
// K2_ERROR: Argument type mismatch: actual type is 'List<Int>', but 'String' was expected.
// K2_ERROR: No context argument for 'x: List<String>' found.

context(x: List<String>)
fun foo(a: String): String = a

fun main() {
    // List<Int> does NOT match List<String> context param
    foo(<caret>listOf(1, 2), a = "World")
}
