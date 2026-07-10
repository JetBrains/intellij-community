// COMPILER_ARGUMENTS: -Xcontext-parameters -XXLanguage:+ExplicitContextArguments
// PRIORITY: LOW
// INTENTION_TEXT: "Add 'x =' to argument"
// AFTER-WARNING: Parameter 'x' is never used
// AFTER-WARNING: Parameter 'a' is never used
// AFTER-WARNING: Parameter 'b' is never used
// K2_ERROR: NO_CONTEXT_ARGUMENT
// K2_ERROR: TOO_MANY_ARGUMENTS


context(x: String)
fun foo3(a: String, b: String): String = x + a

fun main() {
    foo3(a = "World", "Hello", <caret>"a")
}