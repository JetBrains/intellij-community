// COMPILER_ARGUMENTS: -Xcontext-parameters -XXLanguage:+ExplicitContextArguments
// IS_APPLICABLE: false
// K2_ERROR: ARGUMENT_PASSED_TWICE
// K2_ERROR: ARGUMENT_TYPE_MISMATCH
// K2_ERROR: NO_CONTEXT_ARGUMENT


context(x: String)
fun foo(a: String): String = a

fun main() {
    val nullableStr: String? = "Hello"
    // String? is NOT assignable to String, so the intention should NOT be applicable
    foo(<caret>nullableStr, a = "World")
}
