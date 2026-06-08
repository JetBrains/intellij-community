// COMPILER_ARGUMENTS: -Xcontext-parameters -XXLanguage:+ExplicitContextArguments
// IS_APPLICABLE: false

// K2_ERROR: Argument already passed for this parameter.
// K2_ERROR: Argument type mismatch: actual type is 'String?', but 'String' was expected.
// K2_ERROR: No context argument for 'x: String' found.

context(x: String)
fun foo(a: String): String = a

fun main() {
    val nullableStr: String? = "Hello"
    // String? is NOT assignable to String, so the intention should NOT be applicable
    foo(<caret>nullableStr, a = "World")
}
