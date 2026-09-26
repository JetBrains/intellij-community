// COMPILER_ARGUMENTS: -Xcontext-parameters -XXLanguage:+ExplicitContextArguments
// PRIORITY: LOW
// INTENTION_TEXT: "Add 'x =' to argument"
// AFTER-WARNING: Parameter 'x' is never used
// AFTER-WARNING: Parameter 'a' is never used
// AFTER-WARNING: Parameter 'b' is never used
// K2_ERROR: ARGUMENT_PASSED_TWICE
// K2_ERROR: NO_CONTEXT_ARGUMENT


context(x: String)
fun foo(a: String, b: String): String = x + a

fun main() {
    foo(a = "World", <caret>"Hello", b = "a")
}